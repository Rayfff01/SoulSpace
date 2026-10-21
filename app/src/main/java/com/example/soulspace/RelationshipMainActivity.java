package com.example.soulspace;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RelationshipMainActivity extends AppCompatActivity {
    private TextView tvYears;
    private TextView tvMonths;
    private TextView tvDays;
    private TextView tvDailyMessage;
    private TextView tvViewCalendar;
    private TextView tvNoUpcomingDates;
    private ImageView ivAvatar;
    private Button btnSendMessage;
    private View navHome;
    private View navMemories;
    private View navCalendar;
    private View navProfile;
    private ScrollView contentContainer;
    private ProgressBar progressLoading;
    private LinearLayout upcomingDatesRow;
    private DatabaseReference db;
    private String currentUserId;
    private String relationshipId;
    private String partnerId;
    private ValueEventListener dailyMessageListener;
    private boolean timerReady = false;
    private boolean dailyMessageReady = false;
    private boolean eventsReady = false;
    private final List<UpcomingEvent> allEvents = new ArrayList<>();
    private static final int MAX_UPCOMING_CARDS = 3;
    private static final int CARD_HEIGHT_DP = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_relationship_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        UserCache.init(this);
        db = FirebaseDatabase.getInstance().getReference();
        initViews();
        showLoading();
        setListeners();
        loadRelationshipId();
    }
    private void initViews() {
        tvYears = findViewById(R.id.tvYears);
        tvMonths = findViewById(R.id.tvMonths);
        tvDays = findViewById(R.id.tvDays);
        tvDailyMessage = findViewById(R.id.tvDailyMessage);
        tvViewCalendar = findViewById(R.id.tvViewCalendar);
        tvNoUpcomingDates = findViewById(R.id.tvNoUpcomingDates);
        upcomingDatesRow = findViewById(R.id.upcomingDatesRow);
        ivAvatar = findViewById(R.id.ivAvatar);
        btnSendMessage = findViewById(R.id.btnSendMessage);
        navHome = findViewById(R.id.navHome);
        navMemories = findViewById(R.id.navMemories);
        navCalendar = findViewById(R.id.navCalendar);
        navProfile = findViewById(R.id.navProfile);
        contentContainer = findViewById(R.id.contentContainer);
        progressLoading = findViewById(R.id.progressLoading);
    }

    private void showLoading() {
        if (contentContainer != null) {
            contentContainer.setVisibility(View.INVISIBLE);
        }
        if (progressLoading != null) {
            progressLoading.setVisibility(View.VISIBLE);
        }
    }
    private void checkIfReadyToShow() {
        if (!timerReady || !dailyMessageReady || !eventsReady) {
            return;
        }
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
    }
    private void setListeners() {
        if (navHome != null) {
            navHome.setOnClickListener(v -> {});
        }
        if (navMemories != null) {
            navMemories.setOnClickListener(v ->
                    Toast.makeText(this,"Memories — скоро",Toast.LENGTH_SHORT).show());
        }
        if (navCalendar != null) {
            navCalendar.setOnClickListener(v -> {
                startActivity(new Intent(RelationshipMainActivity.this, CalendarActivity.class));
                finish();
            });
        }
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> {
                startActivity(
                        new Intent(RelationshipMainActivity.this, ProfileActivity.class)
                );
                finish();
            });
        }
        if (btnSendMessage != null) {
            btnSendMessage.setOnClickListener(v -> {
                startActivity(
                        new Intent(RelationshipMainActivity.this, DailyMessageActivity.class)
                );
            });
        }
        if (tvViewCalendar != null) {
            tvViewCalendar.setOnClickListener(v -> {
                startActivity(
                        new Intent(RelationshipMainActivity.this, CalendarActivity.class)
                );
            });
        }
    }
    private void loadRelationshipId() {
        String cachedRelationshipId = UserCache.getRelationshipId();
        if (cachedRelationshipId != null && !cachedRelationshipId.isEmpty()) {
            relationshipId = cachedRelationshipId;
            loadRelationshipData();
            return;
        }
        db.child("users")
                .child(currentUserId)
                .child("relationshipId")
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                String id = snapshot.getValue(String.class);
                                if (id == null || id.isEmpty()) {
                                    timerReady = true;
                                    showDailyMessageFallback();
                                    allEvents.clear();
                                    renderUpcomingDates();
                                    eventsReady = true;
                                    checkIfReadyToShow();
                                    return;
                                }
                                relationshipId = id;
                                loadRelationshipData();
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                timerReady = true;
                                showDailyMessageFallback();
                                allEvents.clear();
                                renderUpcomingDates();
                                eventsReady = true;
                                checkIfReadyToShow();
                            }
                        }
                );
    }
    private void loadRelationshipData() {
        loadTimer();
        loadPartnerFromRelationship();
        loadUpcomingEvents();
    }
    private void loadTimer() {
        if (UserCache.hasTimerCache() && UserCache.isTimerCacheFresh()) {
            tvYears.setText(UserCache.getTimerYears());
            tvMonths.setText(UserCache.getTimerMonths());
            tvDays.setText(UserCache.getTimerDays());
            timerReady = true;
            checkIfReadyToShow();
            return;
        }
        loadRelationshipStartDate();
    }
    private void loadRelationshipStartDate() {
        if (relationshipId == null || relationshipId.isEmpty()) {
            setDefaultTimer();
            return;
        }
        db.child("relationships")
                .child(relationshipId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                String user1 = snapshot.child("user1").getValue(String.class);
                                if (user1 == null || user1.isEmpty()) {
                                    setDefaultTimer();
                                    return;
                                }
                                db.child("invites")
                                        .child(user1)
                                        .child("date")
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                                    @Override
                                                    public void onDataChange(@NonNull DataSnapshot dateSnapshot) {
                                                        String startDate = dateSnapshot.getValue(String.class);
                                                        if (startDate == null || startDate.isEmpty()) {
                                                            setDefaultTimer();
                                                            return;
                                                        }
                                                        calculateRelationshipDuration(startDate);
                                                    }
                                                    @Override
                                                    public void onCancelled(@NonNull DatabaseError error) {
                                                        setDefaultTimer();
                                                    }
                                                }
                                        );
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                setDefaultTimer();
                            }
                        }
                );
    }
    private void calculateRelationshipDuration(
            String startDateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            sdf.setLenient(false);
            Date startDate = sdf.parse(startDateString);
            if (startDate == null) {
                setDefaultTimer();
                return;
            }
            Calendar start = Calendar.getInstance();
            start.setTime(startDate);
            Calendar now = Calendar.getInstance();
            int years = now.get(Calendar.YEAR) - start.get(Calendar.YEAR);
            int months = now.get(Calendar.MONTH) - start.get(Calendar.MONTH);
            int days = now.get(Calendar.DAY_OF_MONTH) - start.get(Calendar.DAY_OF_MONTH);
            if (days < 0) {
                months--;
                Calendar previousMonth = (Calendar) now.clone();
                previousMonth.add(Calendar.MONTH, -1);
                days += previousMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
            }
            if (months < 0) {
                years--;
                months += 12;
            }
            if (years < 0) {
                years = 0;
                months = 0;
                days = 0;
            }
            String yearsText = String.format(Locale.getDefault(), "%02d", years);
            String monthsText = String.format(Locale.getDefault(), "%02d", months);
            String daysText = String.format(Locale.getDefault(), "%02d", days);
            tvYears.setText(yearsText);
            tvMonths.setText(monthsText);
            tvDays.setText(daysText);
            UserCache.saveTimerData(yearsText, monthsText, daysText);
            timerReady = true;
            checkIfReadyToShow();
        } catch (Exception e) {
            setDefaultTimer();
        }
    }
    private void setDefaultTimer() {
        tvYears.setText("00");
        tvMonths.setText("00");
        tvDays.setText("00");
        timerReady = true;
        checkIfReadyToShow();
    }
    private void loadPartnerFromRelationship() {
        if (relationshipId == null || relationshipId.isEmpty()) {
            showDailyMessageFallback();
            return;
        }
        db.child("relationships")
                .child(relationshipId)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                String user1 = snapshot.child("user1").getValue(String.class);
                                String user2 = snapshot.child("user2").getValue(String.class);
                                if (currentUserId.equals(user1)) {
                                    partnerId = user2;
                                } else {
                                    partnerId = user1;
                                }
                                if (partnerId == null || partnerId.isEmpty()) {
                                    showDailyMessageFallback();
                                    return;
                                }
                                loadDailyMessage();
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                showDailyMessageFallback();
                            }
                        }
                );
    }

    private void loadDailyMessage() {
        if (partnerId == null || partnerId.isEmpty()) {
            showDailyMessageFallback();
            return;
        }
        DatabaseReference messageRef = db.child("daily_messages").child(partnerId);
        dailyMessageListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String text = snapshot.child("text").getValue(String.class);
                        Long sentAt = snapshot.child("sentAt").getValue(Long.class);
                        if (text == null || text.trim().isEmpty() || sentAt == null) {
                            DailyNoteWidgetUpdater.onNoteLoaded(
                                    RelationshipMainActivity.this, currentUserId, null, 0L);
                            showDailyMessageFallback();
                            return;
                        }
                        long age = System.currentTimeMillis() - sentAt;
                        long oneDay = 24L * 60L * 60L * 1000L;
                        if (age >= 0 && age <= oneDay) {
                            tvDailyMessage.setText(text);
                            DailyNoteWidgetUpdater.onNoteLoaded(
                                    RelationshipMainActivity.this, currentUserId, text, sentAt);
                        } else {
                            DailyNoteWidgetUpdater.onNoteLoaded(
                                    RelationshipMainActivity.this, currentUserId, null, 0L);
                            showDailyMessageFallback();
                        }
                        dailyMessageReady = true;
                        checkIfReadyToShow();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showDailyMessageFallback();
                    }
                };
        messageRef.addValueEventListener(dailyMessageListener);
    }
    private void showDailyMessageFallback() {
        if (tvDailyMessage != null) {
            tvDailyMessage.setText("Сообщений пока нет. " + "Отправь записку партнёру!"
            );
        }
        dailyMessageReady = true;
        checkIfReadyToShow();
    }
    private void loadUpcomingEvents() {
        if (relationshipId == null || relationshipId.isEmpty()) {
            allEvents.clear();
            renderUpcomingDates();
            eventsReady = true;
            checkIfReadyToShow();
            return;
        }
        db.child("relationships")
                .child(relationshipId)
                .child("events")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                allEvents.clear();
                                for (DataSnapshot eventSnapshot : snapshot.getChildren()) {
                                    String eventId = eventSnapshot.getKey();
                                    String title = eventSnapshot.child("title").getValue(String.class);
                                    String date = eventSnapshot.child("date").getValue(String.class);
                                    Boolean repeatYearly = eventSnapshot.child("repeatYearly").getValue(Boolean.class);
                                    if (title == null || title.trim().isEmpty()) {
                                        title = "Важная дата";
                                    }
                                    if (date == null || date.trim().isEmpty()) {
                                        continue;
                                    }
                                    if (repeatYearly == null) {
                                        repeatYearly = false;
                                    }
                                    EventInfo event = new EventInfo(eventId, title, date, repeatYearly);
                                    Calendar nextDate = getNextOccurrenceCalendar(event);
                                    if (nextDate == null) {
                                        continue;
                                    }
                                    Calendar today = Calendar.getInstance();
                                    clearTime(today);
                                    clearTime(nextDate);
                                    long difference = nextDate.getTimeInMillis() - today.getTimeInMillis();
                                    long days = TimeUnit.MILLISECONDS.toDays(difference);
                                    if (days < 0) {
                                        continue;
                                    }
                                    allEvents.add(new UpcomingEvent(event, nextDate, days));
                                }
                                Collections.sort(allEvents, new Comparator<UpcomingEvent>() {
                                            @Override
                                            public int compare(UpcomingEvent first, UpcomingEvent second) {
                                                return Long.compare(
                                                        first.daysLeft,
                                                        second.daysLeft
                                                );
                                            }
                                        }
                                );
                                renderUpcomingDates();
                                eventsReady = true;
                                checkIfReadyToShow();
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                allEvents.clear();
                                renderUpcomingDates();
                                eventsReady = true;
                                checkIfReadyToShow();
                            }
                        }
                );
    }
    private void renderUpcomingDates() {
        if (upcomingDatesRow == null) {
            return;
        }
        upcomingDatesRow.removeAllViews();
        if (allEvents.isEmpty()) {
            upcomingDatesRow.setVisibility(View.GONE);
            if (tvNoUpcomingDates != null) {
                tvNoUpcomingDates.setVisibility(View.VISIBLE);
            }
            return;
        }
        upcomingDatesRow.setVisibility(View.VISIBLE);
        if (tvNoUpcomingDates != null) {
            tvNoUpcomingDates.setVisibility(View.GONE);
        }
        int count = Math.min(MAX_UPCOMING_CARDS, allEvents.size());
        for (int i = 0; i < count; i++) {
            UpcomingEvent upcoming = allEvents.get(i);
            addUpcomingCard(upcoming, i, count);
        }
    }
    private void addUpcomingCard(UpcomingEvent upcoming, int position, int totalCards) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE); // Чистый белый цвет
        background.setCornerRadius(dpToPx(16)); // Слегка скругленные углы
        background.setStroke(dpToPx(1), Color.parseColor("#F0F0F0")); // Очень нежная рамка
        card.setBackground(background);
        card.setElevation(dpToPx(4)); // Добавляем современную тень (API 21+)
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dpToPx(160), 1f);
        int marginRight = (position < totalCards - 1) ? dpToPx(12) : dpToPx(4);
        cardParams.setMargins(dpToPx(4), dpToPx(4), marginRight, dpToPx(8));
        upcomingDatesRow.addView(card, cardParams);
        TextView dateText = new TextView(this);
        dateText.setText(formatShortDate(upcoming.nextDate));
        dateText.setTextSize(12);
        dateText.setTextColor(Color.parseColor("#8E8E93")); // Мягкий серый цвет
        dateText.setGravity(Gravity.CENTER);
        dateText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        card.addView(dateText, dateParams);
        TextView heart = new TextView(this);
        heart.setText("♥");
        heart.setTextSize(16); // Чуть крупнее
        heart.setTextColor(Color.parseColor("#FF6B6B")); // Приятный пастельно-красный/розовый
        heart.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        heartParams.setMargins(0, dpToPx(4), 0, dpToPx(4));
        card.addView(heart, heartParams);
        TextView titleText = new TextView(this);
        titleText.setText(upcoming.event.title);
        titleText.setTextSize(14);
        titleText.setTextColor(Color.parseColor("#1C1C1E")); // Почти черный для хорошей читаемости
        titleText.setGravity(Gravity.CENTER);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setMaxLines(2);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0 // Высота 0, так как используем weight
        );
        titleParams.weight = 1f; // Занимает всё свободное место, выталкивая таймер вниз
        titleParams.topMargin = dpToPx(2);
        titleParams.bottomMargin = dpToPx(8);
        card.addView(titleText, titleParams);
        TextView daysText = new TextView(this);
        daysText.setText(getUpcomingDaysText(upcoming.daysLeft));
        daysText.setTextSize(11);
        daysText.setTextColor(Color.parseColor("#5E5CE6")); // Акцентный цвет (например, индиго)
        daysText.setGravity(Gravity.CENTER);
        daysText.setTypeface(null, android.graphics.Typeface.BOLD);
        daysText.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
        GradientDrawable daysBackground = new GradientDrawable();
        daysBackground.setColor(Color.parseColor("#F2F2F7")); // Светло-серый фон плашки
        daysBackground.setCornerRadius(dpToPx(16)); // Сильное скругление
        daysText.setBackground(daysBackground);
        LinearLayout.LayoutParams daysParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        card.addView(daysText, daysParams);
    }
    private Calendar getNextOccurrenceCalendar(EventInfo event) {
        Calendar parsed = parseDate(event.date);
        if (parsed == null) {
            return null;
        }
        Calendar today = Calendar.getInstance();
        clearTime(today);
        clearTime(parsed);
        if (!event.repeatYearly) {
            if (parsed.before(today)) {
                return null;
            }
            return parsed;
        }
        int month = parsed.get(Calendar.MONTH);
        int day = parsed.get(Calendar.DAY_OF_MONTH);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.YEAR, today.get(Calendar.YEAR));
        next.set(Calendar.MONTH, month);
        next.set(Calendar.DAY_OF_MONTH, day);
        clearTime(next);
        if (next.before(today)) {
            next.add(Calendar.YEAR, 1);
        }
        return next;
    }
    private Calendar parseDate(String dateString) {
        String[] formats = {"dd.MM.yyyy", "yyyy-MM-dd"};
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                Date date = sdf.parse(dateString);
                if (date != null) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    clearTime(calendar);
                    return calendar;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    private String getUpcomingDaysText(long days) {
        if (days == 0) {
            return "Сегодня ♥";
        }
        if (days == 1) {
            return "Завтра";
        }
        return "Через " + days + " " + russianPlural(days, "день", "дня", "дней");
    }
    private String formatShortDate(Calendar calendar) {
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", new Locale("ru", "RU"));
        return sdf.format(calendar.getTime());
    }
    private String russianPlural(long number, String one, String few, String many) {
        long n = number % 100;
        if (n >= 11 && n <= 19) {
            return many;
        }
        long last = number % 10;
        if (last == 1) {
            return one;
        }
        if (last >= 2 && last <= 4) {
            return few;
        }
        return many;
    }
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dailyMessageListener != null && partnerId != null && !partnerId.isEmpty()) {
            db.child("daily_messages").child(partnerId).removeEventListener(dailyMessageListener);
        }
    }
    private static class EventInfo {
        String eventId;
        String title;
        String date;
        boolean repeatYearly;
        EventInfo(String eventId, String title, String date, boolean repeatYearly) {
            this.eventId = eventId;
            this.title = title;
            this.date = date;
            this.repeatYearly = repeatYearly;
        }
    }
    private static class UpcomingEvent {
        EventInfo event;
        Calendar nextDate;
        long daysLeft;
        UpcomingEvent(EventInfo event, Calendar nextDate, long daysLeft) {
            this.event = event;
            this.nextDate = nextDate;
            this.daysLeft = daysLeft;
        }
    }
}