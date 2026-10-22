package com.example.soulspace;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarActivity extends AppCompatActivity {
    private GridLayout calendarGrid;
    private TextView tvMonth;
    private TextView tvEmptyState;
    private Button btnAddEvent;
    private TextView btnPreviousMonth;
    private TextView btnNextMonth;
    private TextView btnCalendarMode;
    private TextView btnListMode;
    private LinearLayout calendarContainer;
    private ScrollView listContainer;
    private LinearLayout listContentContainer;
    private LinearLayout navHome;
    private LinearLayout navMemories;
    private LinearLayout navCalendar;
    private LinearLayout navProfile;
    private View contentContainer;
    private ProgressBar progressLoading;
    private DatabaseReference db;
    private String currentUserId;
    private String relationshipId;
    private ValueEventListener eventsListener;
    private final Map<String, List<EventInfo>> eventsByDate = new HashMap<>();
    private boolean eventsLoadedOnce = false;
    private Calendar displayedMonth;
    private boolean listMode = false;
    private static final int COLOR_TEXT = Color.rgb(45, 45, 45);
    private static final int COLOR_SECONDARY = Color.rgb(138, 138, 138);
    private static final int COLOR_ACCENT = Color.rgb(92, 84, 112);
    private static final int COLOR_EVENT = Color.rgb(244, 232, 237);
    private static final float SWIPE_HORIZONTAL_BIAS = 1.5f;
    private int swipeTouchSlop;
    private int swipeMinimumDistance;
    private boolean swipeTracking;
    private boolean swipeInProgress;
    private float swipeStartX;
    private float swipeStartY;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        UserCache.init(this);
        db = FirebaseDatabase.getInstance().getReference();
        displayedMonth = Calendar.getInstance();
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
        initViews();
        initSwipeNavigation();
        showLoading();
        setListeners();
        resolveRelationshipId();
    }
    private void initViews() {
        calendarGrid = findViewById(R.id.calendarGrid);
        tvMonth = findViewById(R.id.tvMonth);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnAddEvent = findViewById(R.id.btnAddEvent);
        btnPreviousMonth = findViewById(R.id.btnPreviousMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        btnCalendarMode = findViewById(R.id.btnCalendarMode);
        btnListMode = findViewById(R.id.btnListMode);
        calendarContainer = findViewById(R.id.calendarContainer);
        listContainer = findViewById(R.id.listContainer);
        listContentContainer = findViewById(R.id.listContentContainer);
        navHome = findViewById(R.id.navHome);
        navMemories = findViewById(R.id.navMemories);
        navCalendar = findViewById(R.id.navCalendar);
        navProfile = findViewById(R.id.navProfile);
        contentContainer = findViewById(R.id.contentContainer);
        progressLoading = findViewById(R.id.progressLoading);
    }
    private void initSwipeNavigation() {
        ViewConfiguration configuration = ViewConfiguration.get(this);
        swipeTouchSlop = configuration.getScaledTouchSlop();
        swipeMinimumDistance = Math.max(swipeTouchSlop * 4, dp(48));
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            resetSwipeState();
            swipeTracking = canStartSwipe(event);
            if (swipeTracking) {
                swipeStartX = event.getRawX();
                swipeStartY = event.getRawY();
            }
            boolean handled = super.dispatchTouchEvent(event);
            return handled || swipeTracking;
        }

        if (!swipeTracking) {
            return super.dispatchTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            resetSwipeState();
            return super.dispatchTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getRawX() - swipeStartX;
            float dy = event.getRawY() - swipeStartY;
            float absX = Math.abs(dx);
            float absY = Math.abs(dy);

            if (!swipeInProgress) {
                if (absY > swipeTouchSlop && absY >= absX) {
                    resetSwipeState();
                    return super.dispatchTouchEvent(event);
                }
                if (absX >= swipeMinimumDistance && absX > absY * SWIPE_HORIZONTAL_BIAS) {
                    swipeInProgress = true;
                    cancelChildTouch(event);
                }
            }

            if (swipeInProgress) {
                return true;
            }
            return super.dispatchTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_UP) {
            float dx = event.getRawX() - swipeStartX;
            float dy = event.getRawY() - swipeStartY;
            float absX = Math.abs(dx);
            float absY = Math.abs(dy);
            boolean horizontalSwipe = absX >= swipeMinimumDistance
                    && absX > absY * SWIPE_HORIZONTAL_BIAS;

            if (!swipeInProgress && horizontalSwipe) {
                swipeInProgress = true;
                cancelChildTouch(event);
            }

            boolean consumed = swipeInProgress;
            if (horizontalSwipe) {
                changeMonth(dx < 0 ? 1 : -1);
            }

            resetSwipeState();
            return consumed || super.dispatchTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = swipeInProgress;
            resetSwipeState();
            return consumed || super.dispatchTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }
    private boolean canStartSwipe(MotionEvent event) {
        if (listMode
                || contentContainer.getVisibility() != View.VISIBLE
                || calendarContainer.getVisibility() != View.VISIBLE) {
            return false;
        }
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        if (!isPointInsideView(calendarContainer, rawX, rawY)) {
            return false;
        }
        return !isPointInsideView(btnPreviousMonth, rawX, rawY)
                && !isPointInsideView(btnNextMonth, rawX, rawY);
    }
    private boolean isPointInsideView(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE
                || view.getWidth() == 0 || view.getHeight() == 0) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0]
                && rawX < location[0] + view.getWidth()
                && rawY >= location[1]
                && rawY < location[1] + view.getHeight();
    }
    private void cancelChildTouch(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }
    private void resetSwipeState() {
        swipeTracking = false;
        swipeInProgress = false;
        swipeStartX = 0f;
        swipeStartY = 0f;
    }
    private void showLoading() {
        contentContainer.setVisibility(View.INVISIBLE);
        progressLoading.setVisibility(View.VISIBLE);
    }
    private void showContent() {
        progressLoading.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
    }
    private void resolveRelationshipId() {
        String cached = UserCache.getRelationshipId();
        if (cached != null) {
            relationshipId = cached;
            listenForEvents();
            return;
        }
        db.child("users").child(currentUserId).child("relationshipId").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot snapshot) {
                                relationshipId = snapshot.getValue(String.class);
                                if (relationshipId == null) {
                                    showContent();
                                    renderAll();
                                    return;
                                }
                                listenForEvents();
                            }
                            @Override
                            public void onCancelled(DatabaseError error) {
                                showContent();
                                renderAll();
                            }
                        }
                );
    }
    private void listenForEvents() {
        eventsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        eventsByDate.clear();
                        for (DataSnapshot eventSnapshot : snapshot.getChildren()) {
                            String eventId = eventSnapshot.getKey();
                            String title = eventSnapshot.child("title").getValue(String.class);
                            String date = eventSnapshot.child("date").getValue(String.class);
                            String addedBy = eventSnapshot.child("addedBy").getValue(String.class);
                            Long createdAt = eventSnapshot.child("createdAt").getValue(Long.class);
                            Boolean repeatYearly = eventSnapshot.child("repeatYearly").getValue(Boolean.class);
                            if (date == null) {
                                continue;
                            }
                            EventInfo event = new EventInfo(eventId, title != null ? title : "", date, addedBy,
                                            createdAt != null ? createdAt : 0, repeatYearly != null && repeatYearly);
                            if (!eventsByDate.containsKey(date)) {
                                eventsByDate.put(date, new ArrayList<>());
                            }
                            eventsByDate.get(date).add(event);
                        }
                        renderAll();
                        if (!eventsLoadedOnce) {
                            eventsLoadedOnce = true;
                            showContent();
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        if (!eventsLoadedOnce) {
                            eventsLoadedOnce = true;
                            showContent();
                        }
                    }
                };
        db.child("relationships").child(relationshipId).child("events").addValueEventListener(eventsListener);
    }
    private void renderAll() {
        renderCalendar();
        renderList();
        updateModeButtons();
    }
    private void renderCalendar() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("LLLL yyyy", new Locale("ru", "RU"));
        String monthText = monthFormat.format(displayedMonth.getTime());
        if (!monthText.isEmpty()) {
            monthText = monthText.substring(0, 1).toUpperCase() + monthText.substring(1);
        }
        tvMonth.setText(monthText);
        calendarGrid.removeAllViews();
        Calendar firstDay = (Calendar) displayedMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);
        int dayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK);
        int startOffset = dayOfWeek - Calendar.MONDAY;
        if (startOffset < 0) {
            startOffset += 7;
        }
        int daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < startOffset; i++) {
            addEmptyCalendarCell();
        }
        for (int day = 1; day <= daysInMonth; day++) {
            Calendar date = (Calendar) displayedMonth.clone();
            date.set(Calendar.DAY_OF_MONTH, day);
            List<EventInfo> events = getEventsForDate(date);
            boolean hasEvent = events != null && !events.isEmpty();
            boolean isToday = isToday(date);
            addCalendarDay(day, hasEvent, isToday);
        }
        int totalCells = startOffset + daysInMonth;
        int remainingCells = 7 - (totalCells % 7);
        if (remainingCells < 7) {
            for (int i = 0; i < remainingCells; i++) {
                addEmptyCalendarCell();
            }
        }
        if (eventsByDate.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
    }
    private void addEmptyCalendarCell() {
        TextView cell = new TextView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(52);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        cell.setLayoutParams(params);
        calendarGrid.addView(cell);
    }
    private void addCalendarDay(int day, boolean hasEvent, boolean isToday) {
        TextView dayView = new TextView(this);
        dayView.setText(String.valueOf(day));
        dayView.setTextSize(14);
        dayView.setGravity(Gravity.CENTER);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(52);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        dayView.setLayoutParams(params);
        if (hasEvent) {
            dayView.setBackgroundResource(R.drawable.calendar_event_day);
            dayView.setTextColor(Color.rgb(92, 55, 68));
            dayView.setTypeface(dayView.getTypeface(), Typeface.BOLD);
        } else if (isToday) {
            dayView.setBackgroundResource(R.drawable.calendar_today);
            dayView.setTextColor(Color.WHITE);
            dayView.setTypeface(dayView.getTypeface(), Typeface.BOLD);
        } else {
            dayView.setTextColor(COLOR_TEXT);
        }
        dayView.setOnClickListener(v -> {
                    Calendar clickedDate = (Calendar) displayedMonth.clone();
                    clickedDate.set(Calendar.DAY_OF_MONTH, day);
                    List<EventInfo> events = getEventsForDate(clickedDate);
                    if (events != null && !events.isEmpty()) {
                        showEventsDialog(formatDate(clickedDate), events);
                    } else {
                        showEmptyDateDialog(formatDate(clickedDate));
                    }
                }
        );
        calendarGrid.addView(dayView);
    }
    private void renderList() {
        listContentContainer.removeAllViews();
        List<EventInfo> allEvents = getAllEvents();
        if (allEvents.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Пока нет важных дат.\n\n" + "Добавьте первую дату вашей пары ♥");
            empty.setTextSize(14);
            empty.setTextColor(COLOR_SECONDARY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(40), dp(20), dp(40));
            listContentContainer.addView(empty);
            return;
        }
        for (EventInfo event : allEvents) {
            listContentContainer.addView(buildListEvent(event));
        }
    }
    private View buildListEvent(EventInfo event) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(12), dp(14));
        row.setBackgroundResource(R.drawable.date_card_background);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(10);
        row.setLayoutParams(rowParams);
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textColumn.setLayoutParams(textParams);
        TextView title = new TextView(this);
        title.setText("♥  " + event.title);
        title.setTextSize(15);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        textColumn.addView(title);
        TextView date = new TextView(this);
        if (event.repeatYearly) {
            date.setText(getYearlyDateText(event));
        } else {
            date.setText(formatReadableDate(event.date));
        }
        date.setTextSize(13);
        date.setTextColor(COLOR_SECONDARY);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(5);
        date.setLayoutParams(dateParams);
        textColumn.addView(date);
        TextView countdown = new TextView(this);
        String countdownText = getCountdownText(event);
        if (countdownText != null && !countdownText.isEmpty()) {
            countdown.setText(countdownText);
            countdown.setTextSize(12);
            countdown.setTextColor(COLOR_ACCENT);
            LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            countdownParams.topMargin = dp(4);
            countdown.setLayoutParams(countdownParams);
            textColumn.addView(countdown);
        }
        row.addView(textColumn);
        TextView delete = new TextView(this);
        delete.setText("✕");
        delete.setTextSize(16);
        delete.setTextColor(Color.rgb(160, 160, 160));
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(12), dp(10), dp(6), dp(10));
        delete.setClickable(true);
        delete.setOnClickListener(v -> confirmDeleteEvent(event.eventId, event.title));
        row.addView(delete);
        row.setClickable(true);
        row.setOnClickListener(v -> {showEventsDialog(event.date, Collections.singletonList(event));});
        return row;
    }

    // =========================
    // All events
    // =========================

    private List<EventInfo> getAllEvents() {

        List<EventInfo> result =
                new ArrayList<>();

        for (
                List<EventInfo> events
                : eventsByDate.values()
        ) {

            result.addAll(
                    events
            );
        }

        Collections.sort(
                result,
                new Comparator<EventInfo>() {

                    @Override
                    public int compare(
                            EventInfo first,
                            EventInfo second
                    ) {

                        long firstTime =
                                getNextOccurrence(
                                        first
                                );

                        long secondTime =
                                getNextOccurrence(
                                        second
                                );

                        return Long.compare(
                                firstTime,
                                secondTime
                        );
                    }
                }
        );

        return result;
    }

    // =========================
    // Next occurrence
    // =========================

    private long getNextOccurrence(
            EventInfo event
    ) {

        try {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "dd.MM.yyyy",
                            Locale.getDefault()
                    );

            Calendar original =
                    Calendar.getInstance();

            original.setTime(
                    format.parse(
                            event.date
                    )
            );

            Calendar today =
                    Calendar.getInstance();

            Calendar next =
                    Calendar.getInstance();

            if (event.repeatYearly) {

                next.set(
                        Calendar.YEAR,
                        today.get(
                                Calendar.YEAR
                        )
                );

                next.set(
                        Calendar.MONTH,
                        original.get(
                                Calendar.MONTH
                        )
                );

                next.set(
                        Calendar.DAY_OF_MONTH,
                        original.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

                if (
                        next.before(today)
                ) {

                    next.add(
                            Calendar.YEAR,
                            1
                    );
                }

            } else {

                next.setTime(
                        original.getTime()
                );
            }

            return next.getTimeInMillis();

        } catch (Exception e) {

            return Long.MAX_VALUE;
        }
    }

    // =========================
    // Yearly date text
    // =========================

    private String getYearlyDateText(
            EventInfo event
    ) {

        try {

            SimpleDateFormat input =
                    new SimpleDateFormat(
                            "dd.MM.yyyy",
                            Locale.getDefault()
                    );

            SimpleDateFormat output =
                    new SimpleDateFormat(
                            "d MMMM",
                            new Locale(
                                    "ru",
                                    "RU"
                            )
                    );

            return output.format(
                    input.parse(
                            event.date
                    )
            ) + " · каждый год";

        } catch (Exception e) {

            return event.date
                    + " · каждый год";
        }
    }

    // =========================
    // Countdown
    // =========================

    private String getCountdownText(
            EventInfo event
    ) {

        long next =
                getNextOccurrence(
                        event
                );

        if (
                next == Long.MAX_VALUE
        ) {

            return "";
        }

        Calendar today =
                Calendar.getInstance();

        Calendar target =
                Calendar.getInstance();

        target.setTimeInMillis(
                next
        );

        clearTime(today);

        clearTime(target);

        long difference =
                target.getTimeInMillis()
                        - today.getTimeInMillis();

        long days =
                difference
                        / (
                        24L
                                * 60L
                                * 60L
                                * 1000L
                );

        if (days == 0) {

            return "Сегодня ♥";

        } else if (days == 1) {

            return "Завтра";

        } else if (days < 7) {

            return "Через "
                    + days
                    + " дней";

        } else if (days < 30) {

            long weeks =
                    days / 7;

            return "Через "
                    + weeks
                    + " "
                    + russianPlural(
                    weeks,
                    "неделю",
                    "недели",
                    "недель"
            );

        } else {

            long months =
                    days / 30;

            return "Через "
                    + months
                    + " "
                    + russianPlural(
                    months,
                    "месяц",
                    "месяца",
                    "месяцев"
            );
        }
    }

    private String russianPlural(
            long number,
            String one,
            String few,
            String many
    ) {

        long n =
                number % 100;

        if (
                n >= 11
                        && n <= 14
        ) {

            return many;
        }

        n =
                number % 10;

        if (n == 1) {
            return one;
        }

        if (
                n >= 2
                        && n <= 4
        ) {

            return few;
        }

        return many;
    }

    private void clearTime(
            Calendar calendar
    ) {

        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
    }

    // =========================
    // Events for date
    // =========================

    private List<EventInfo> getEventsForDate(
            Calendar calendar
    ) {

        List<EventInfo> result =
                new ArrayList<>();

        String fullDate =
                formatDate(
                        calendar
                );

        List<EventInfo> exactEvents =
                eventsByDate.get(
                        fullDate
                );

        if (exactEvents != null) {

            result.addAll(
                    exactEvents
            );
        }

        int currentDay =
                calendar.get(
                        Calendar.DAY_OF_MONTH
                );

        int currentMonth =
                calendar.get(
                        Calendar.MONTH
                ) + 1;

        for (
                List<EventInfo> events
                : eventsByDate.values()
        ) {

            for (
                    EventInfo event
                    : events
            ) {

                if (!event.repeatYearly) {
                    continue;
                }

                try {

                    SimpleDateFormat format =
                            new SimpleDateFormat(
                                    "dd.MM.yyyy",
                                    Locale.getDefault()
                            );

                    Calendar original =
                            Calendar.getInstance();

                    original.setTime(
                            format.parse(
                                    event.date
                            )
                    );

                    int originalDay =
                            original.get(
                                    Calendar.DAY_OF_MONTH
                            );

                    int originalMonth =
                            original.get(
                                    Calendar.MONTH
                            ) + 1;

                    if (
                            currentDay
                                    == originalDay
                                    &&
                                    currentMonth
                                            == originalMonth
                    ) {

                        if (
                                !result.contains(
                                        event
                                )
                        ) {

                            result.add(
                                    event
                            );
                        }
                    }

                } catch (Exception ignored) {
                }
            }
        }

        return result;
    }

    // =========================
    // Event dialog
    // =========================

    private void showEventsDialog(
            String date,
            List<EventInfo> events
    ) {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        int padding =
                dp(20);

        layout.setPadding(
                padding,
                dp(4),
                padding,
                dp(8)
        );

        TextView dateTitle =
                new TextView(this);

        dateTitle.setText(
                formatReadableDate(
                        date
                )
        );

        dateTitle.setTextSize(
                13
        );

        dateTitle.setTextColor(
                COLOR_SECONDARY
        );

        layout.addView(
                dateTitle
        );

        for (
                EventInfo event
                : events
        ) {

            TextView title =
                    new TextView(this);

            title.setText(
                    "♥  " + event.title
            );

            title.setTextSize(
                    17
            );

            title.setTextColor(
                    COLOR_TEXT
            );

            title.setTypeface(
                    title.getTypeface(),
                    Typeface.BOLD
            );

            LinearLayout.LayoutParams titleParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            titleParams.topMargin =
                    dp(16);

            title.setLayoutParams(
                    titleParams
            );

            layout.addView(
                    title
            );

            TextView yearInfo =
                    new TextView(this);

            if (event.repeatYearly) {

                yearInfo.setText(
                        "Произошло "
                                + formatReadableDate(
                                event.date
                        )
                                + "\n"
                                + "Повторяется каждый год ♥"
                );

            } else {

                yearInfo.setText(
                        "Дата: "
                                + formatReadableDate(
                                event.date
                        )
                );
            }

            yearInfo.setTextSize(
                    13
            );

            yearInfo.setTextColor(
                    COLOR_SECONDARY
            );

            LinearLayout.LayoutParams infoParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            infoParams.topMargin =
                    dp(6);

            yearInfo.setLayoutParams(
                    infoParams
            );

            layout.addView(
                    yearInfo
            );

            TextView delete =
                    new TextView(this);

            delete.setText(
                    "Удалить"
            );

            delete.setTextSize(
                    13
            );

            delete.setTextColor(
                    Color.rgb(
                            170,
                            90,
                            105
                    )
            );

            delete.setPadding(
                    0,
                    dp(10),
                    0,
                    dp(4)
            );

            delete.setClickable(
                    true
            );

            delete.setOnClickListener(
                    v ->
                            confirmDeleteEvent(
                                    event.eventId,
                                    event.title
                            )
            );

            layout.addView(
                    delete
            );
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Важная дата"
                )
                .setView(
                        layout
                )
                .setPositiveButton(
                        "Закрыть",
                        null
                )
                .show();
    }

    // =========================
    // Empty date
    // =========================

    private void showEmptyDateDialog(
            String date
    ) {

        new AlertDialog.Builder(this)
                .setTitle(
                        formatReadableDate(
                                date
                        )
                )
                .setMessage(
                        "На эту дату пока ничего "
                                + "не запланировано."
                )
                .setPositiveButton(
                        "Закрыть",
                        null
                )
                .show();
    }

    // =========================
    // Add event
    // =========================

    private void openAddEventDialog() {

        if (
                relationshipId == null
        ) {

            Toast.makeText(
                    this,
                    "Сначала создайте связь с партнёром",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        LinearLayout dialogLayout =
                new LinearLayout(this);

        dialogLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        int pad =
                dp(20);

        dialogLayout.setPadding(
                pad,
                pad,
                pad,
                pad
        );

        EditText etTitle =
                new EditText(this);

        etTitle.setHint(
                "Например: Первый поцелуй"
        );

        etTitle.setInputType(
                InputType.TYPE_CLASS_TEXT
        );

        dialogLayout.addView(
                etTitle
        );

        TextView tvPickedDate =
                new TextView(this);

        tvPickedDate.setText(
                "Выбрать дату"
        );

        tvPickedDate.setTextSize(
                15
        );

        tvPickedDate.setTextColor(
                COLOR_ACCENT
        );

        tvPickedDate.setPadding(
                0,
                dp(18),
                0,
                dp(8)
        );

        tvPickedDate.setClickable(
                true
        );

        dialogLayout.addView(
                tvPickedDate
        );

        final String[] pickedDate =
                {null};

        tvPickedDate.setOnClickListener(
                v -> {

                    Calendar calendar =
                            Calendar.getInstance();

                    DatePickerDialog picker =
                            new DatePickerDialog(
                                    this,
                                    (
                                            DatePicker view,
                                            int year,
                                            int month,
                                            int day
                                    ) -> {

                                        String formatted =
                                                String.format(
                                                        Locale.getDefault(),
                                                        "%02d.%02d.%04d",
                                                        day,
                                                        month + 1,
                                                        year
                                                );

                                        pickedDate[0] =
                                                formatted;

                                        tvPickedDate.setText(
                                                "♥  "
                                                        + formatted
                                        );
                                    },
                                    calendar.get(
                                            Calendar.YEAR
                                    ),
                                    calendar.get(
                                            Calendar.MONTH
                                    ),
                                    calendar.get(
                                            Calendar.DAY_OF_MONTH
                                    )
                            );

                    picker.show();
                }
        );

        CheckBox cbRepeatYearly =
                new CheckBox(this);

        cbRepeatYearly.setText(
                "Повторять каждый год"
        );

        cbRepeatYearly.setTextSize(
                14
        );

        cbRepeatYearly.setTextColor(
                COLOR_TEXT
        );

        dialogLayout.addView(
                cbRepeatYearly
        );

        TextView repeatHint =
                new TextView(this);

        repeatHint.setText(
                "Например, «Первый поцелуй» "
                        + "будет отмечаться каждый год."
        );

        repeatHint.setTextSize(
                12
        );

        repeatHint.setTextColor(
                COLOR_SECONDARY
        );

        dialogLayout.addView(
                repeatHint
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Новая важная дата"
                )
                .setView(
                        dialogLayout
                )
                .setPositiveButton(
                        "Добавить",
                        (dialog, which) -> {

                            String title =
                                    etTitle
                                            .getText()
                                            .toString()
                                            .trim();

                            if (
                                    title.isEmpty()
                                            || pickedDate[0] == null
                            ) {

                                Toast.makeText(
                                        this,
                                        "Укажите название и дату",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            saveEvent(
                                    title,
                                    pickedDate[0],
                                    cbRepeatYearly.isChecked()
                            );
                        }
                )
                .setNegativeButton(
                        "Отмена",
                        null
                )
                .show();
    }

    // =========================
    // Save
    // =========================

    // =========================
// Save
// =========================

    private void saveEvent(
            String title,
            String date,
            boolean repeatYearly
    ) {

        if (relationshipId == null) {
            Toast.makeText(
                    this,
                    "Сначала создайте связь с партнёром",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String eventId = db
                .child("relationships")
                .child(relationshipId)
                .child("events")
                .push()
                .getKey();

        if (eventId == null) {
            Toast.makeText(
                    this,
                    "Не удалось создать дату",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        long createdAt = System.currentTimeMillis();

        Map<String, Object> event = new HashMap<>();

        event.put(
                "title",
                title
        );

        event.put(
                "date",
                date
        );

        event.put(
                "addedBy",
                currentUserId
        );

        event.put(
                "createdAt",
                createdAt
        );

        event.put(
                "repeatYearly",
                repeatYearly
        );

        db.child("relationships")
                .child(relationshipId)
                .child("events")
                .child(eventId)
                .setValue(event)
                .addOnSuccessListener(
                        unused -> {

                            /*
                             * ВАЖНО:
                             *
                             * Здесь НЕ добавляем event вручную
                             * в eventsByDate.
                             *
                             * Firebase listener сам вызовет
                             * onDataChange(), очистит старые данные
                             * и загрузит актуальный список.
                             */

                            Toast.makeText(
                                    this,
                                    "Дата добавлена ♥",
                                    Toast.LENGTH_SHORT
                            ).show();

                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Не удалось сохранить дату",
                                    Toast.LENGTH_SHORT
                            ).show();

                        }
                );
    }

    // =========================
    // Delete
    // =========================

    private void confirmDeleteEvent(
            String eventId,
            String title
    ) {

        new AlertDialog.Builder(this)
                .setTitle(
                        "Удалить дату?"
                )
                .setMessage(
                        "«"
                                + title
                                + "» будет удалена "
                                + "для вас обоих."
                )
                .setPositiveButton(
                        "Удалить",
                        (dialog, which) -> {

                            if (
                                    relationshipId
                                            == null
                            ) {

                                return;
                            }

                            db.child("relationships")
                                    .child(relationshipId)
                                    .child("events")
                                    .child(eventId)
                                    .removeValue()
                                    .addOnSuccessListener(
                                            unused -> {

                                                for (
                                                        List<EventInfo> events
                                                        : eventsByDate.values()
                                                ) {

                                                    events.removeIf(
                                                            event ->
                                                                    event.eventId
                                                                            .equals(
                                                                                    eventId
                                                                            )
                                                    );
                                                }

                                                eventsByDate
                                                        .entrySet()
                                                        .removeIf(
                                                                entry ->
                                                                        entry.getValue()
                                                                                .isEmpty()
                                                        );

                                                renderAll();

                                                Toast.makeText(
                                                        this,
                                                        "Дата удалена",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            }
                                    )
                                    .addOnFailureListener(
                                            e ->
                                                    Toast.makeText(
                                                            this,
                                                            "Не удалось удалить дату",
                                                            Toast.LENGTH_SHORT
                                                    ).show()
                                    );
                        }
                )
                .setNegativeButton(
                        "Отмена",
                        null
                )
                .show();
    }

    // =========================
    // Mode switching
    // =========================

    private void setCalendarMode() {

        listMode = false;

        calendarContainer.setVisibility(
                View.VISIBLE
        );

        listContainer.setVisibility(
                View.GONE
        );

        updateModeButtons();
    }

    private void setListMode() {

        listMode = true;

        calendarContainer.setVisibility(
                View.GONE
        );

        listContainer.setVisibility(
                View.VISIBLE
        );

        renderList();

        updateModeButtons();
    }

    private void updateModeButtons() {

        if (listMode) {

            btnListMode.setTextColor(
                    COLOR_ACCENT
            );

            btnCalendarMode.setTextColor(
                    COLOR_SECONDARY
            );

        } else {

            btnCalendarMode.setTextColor(
                    COLOR_ACCENT
            );

            btnListMode.setTextColor(
                    COLOR_SECONDARY
            );
        }
    }

    // =========================
    // Month
    // =========================

    private void changeMonth(
            int amount
    ) {

        displayedMonth.add(
                Calendar.MONTH,
                amount
        );

        renderCalendar();
    }

    // =========================
    // Listeners
    // =========================

    private void setListeners() {

        btnAddEvent.setOnClickListener(
                v ->
                        openAddEventDialog()
        );

        btnPreviousMonth.setOnClickListener(
                v ->
                        changeMonth(-1)
        );

        btnNextMonth.setOnClickListener(
                v ->
                        changeMonth(1)
        );

        btnCalendarMode.setOnClickListener(
                v ->
                        setCalendarMode()
        );

        btnListMode.setOnClickListener(
                v ->
                        setListMode()
        );

        navHome.setOnClickListener(v -> {

            startActivity(
                    new Intent(
                            this,
                            RelationshipMainActivity.class
                    )
            );

            finish();
        });

        navMemories.setOnClickListener(
                v ->
                        Toast.makeText(
                                this,
                                "Memories — скоро",
                                Toast.LENGTH_SHORT
                        ).show()
        );

        navCalendar.setOnClickListener(
                v -> {
                }
        );

        navProfile.setOnClickListener(v -> {

            startActivity(
                    new Intent(
                            this,
                            ProfileActivity.class
                    )
            );

            finish();
        });
    }

    // =========================
    // Formatting
    // =========================

    private String formatDate(
            Calendar calendar
    ) {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "dd.MM.yyyy",
                        Locale.getDefault()
                );

        return format.format(
                calendar.getTime()
        );
    }

    private String formatReadableDate(
            String date
    ) {

        try {

            SimpleDateFormat input =
                    new SimpleDateFormat(
                            "dd.MM.yyyy",
                            Locale.getDefault()
                    );

            SimpleDateFormat output =
                    new SimpleDateFormat(
                            "d MMMM yyyy",
                            new Locale(
                                    "ru",
                                    "RU"
                            )
                    );

            return output.format(
                    input.parse(date)
            );

        } catch (Exception e) {

            return date;
        }
    }

    // =========================
    // Today
    // =========================

    private boolean isToday(
            Calendar calendar
    ) {

        Calendar today =
                Calendar.getInstance();

        return calendar.get(
                Calendar.YEAR
        ) == today.get(
                Calendar.YEAR
        )
                &&
                calendar.get(
                        Calendar.MONTH
                ) == today.get(
                        Calendar.MONTH
                )
                &&
                calendar.get(
                        Calendar.DAY_OF_MONTH
                ) == today.get(
                        Calendar.DAY_OF_MONTH
                );
    }

    // =========================
    // dp
    // =========================

    private int dp(
            int value
    ) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int)
                (
                        value * density
                                + 0.5f
                );
    }

    // =========================
    // Destroy
    // =========================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (
                relationshipId != null
                        && eventsListener != null
        ) {

            db.child("relationships")
                    .child(relationshipId)
                    .child("events")
                    .removeEventListener(
                            eventsListener
                    );
        }
    }

    // =========================
    // Event model
    // =========================

    private static class EventInfo {

        String eventId;
        String title;
        String date;
        String addedBy;

        long createdAt;

        boolean repeatYearly;

        EventInfo(
                String eventId,
                String title,
                String date,
                String addedBy,
                long createdAt,
                boolean repeatYearly
        ) {

            this.eventId =
                    eventId;

            this.title =
                    title;

            this.date =
                    date;

            this.addedBy =
                    addedBy;

            this.createdAt =
                    createdAt;

            this.repeatYearly =
                    repeatYearly;
        }
    }
}