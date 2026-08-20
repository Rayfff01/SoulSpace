package com.example.soulspace;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;

public class CalendarActivity extends AppCompatActivity {

    private LinearLayout eventsContainer;
    private TextView tvEmptyState;
    private Button btnAddEvent;
    private LinearLayout navHome, navMemories, navCalendar, navProfile;

    private View contentContainer;
    private ProgressBar progressLoading;

    private DatabaseReference db;
    private String currentUserId;
    private String relationshipId;
    private ValueEventListener eventsListener;
    private boolean eventsLoadedOnce = false;

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

        initViews();
        showLoading();
        setListeners();
        resolveRelationshipId();
    }

    private void initViews() {
        eventsContainer = findViewById(R.id.eventsContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnAddEvent = findViewById(R.id.btnAddEvent);
        navHome = findViewById(R.id.navHome);
        navMemories = findViewById(R.id.navMemories);
        navCalendar = findViewById(R.id.navCalendar);
        navProfile = findViewById(R.id.navProfile);

        contentContainer = findViewById(R.id.contentContainer);
        progressLoading = findViewById(R.id.progressLoading);
    }

    private void showLoading() {
        contentContainer.setVisibility(View.INVISIBLE);
        progressLoading.setVisibility(View.VISIBLE);
    }

    private void showContent() {
        progressLoading.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
    }

    // Достаём relationshipId — сначала из локального кэша, если нет — из Firebase
    private void resolveRelationshipId() {
        String cached = UserCache.getRelationshipId();
        if (cached != null) {
            relationshipId = cached;
            listenForEvents();
            return;
        }

        db.child("users").child(currentUserId).child("relationshipId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        relationshipId = snapshot.getValue(String.class);
                        if (relationshipId == null) {
                            showContent(); // нечего грузить — просто показываем пустой экран
                            return;
                        }
                        listenForEvents();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showContent();
                    }
                });
    }

    private void listenForEvents() {
        eventsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                renderEvents(snapshot);
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

        db.child("relationships").child(relationshipId).child("events")
                .addValueEventListener(eventsListener);
    }

    private void renderEvents(DataSnapshot snapshot) {
        // Убираем всё, кроме заглушки пустого состояния
        for (int i = eventsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = eventsContainer.getChildAt(i);
            if (child != tvEmptyState) {
                eventsContainer.removeViewAt(i);
            }
        }

        if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        tvEmptyState.setVisibility(View.GONE);

        for (DataSnapshot eventSnapshot : snapshot.getChildren()) {
            String eventId = eventSnapshot.getKey();
            String title = eventSnapshot.child("title").getValue(String.class);
            String date = eventSnapshot.child("date").getValue(String.class);
            eventsContainer.addView(buildEventRow(eventId, title, date));
        }
    }

    private View buildEventRow(String eventId, String title, String date) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.date_card_background);
        int pad = dp(14);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(10);
        row.setLayoutParams(rowParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textColumn.setLayoutParams(textParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title != null ? title : "");
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(0xFF2D2D2D);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        textColumn.addView(tvTitle);

        TextView tvDate = new TextView(this);
        tvDate.setText(date != null ? date : "");
        tvDate.setTextSize(12);
        tvDate.setTextColor(0xFF8A8A8A);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(4);
        tvDate.setLayoutParams(dateParams);
        textColumn.addView(tvDate);

        row.addView(textColumn);

        TextView tvDelete = new TextView(this);
        tvDelete.setText("✕");
        tvDelete.setTextSize(16);
        tvDelete.setTextColor(0xFFA0A0A0);
        tvDelete.setPadding(dp(8), dp(8), dp(8), dp(8));
        tvDelete.setClickable(true);
        tvDelete.setFocusable(true);
        tvDelete.setOnClickListener(v -> confirmDeleteEvent(eventId, title));
        row.addView(tvDelete);

        return row;
    }

    private void confirmDeleteEvent(String eventId, String title) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить дату?")
                .setMessage(title != null ? "\"" + title + "\" будет удалена для вас обоих." : "Дата будет удалена для вас обоих.")
                .setPositiveButton("Удалить", (dialog, which) ->
                        db.child("relationships").child(relationshipId)
                                .child("events").child(eventId)
                                .removeValue())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openAddEventDialog() {
        if (relationshipId == null) {
            Toast.makeText(this, "Сначала создайте связь с партнёром", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        dialogLayout.setPadding(pad, pad, pad, pad);

        final EditText etTitle = new EditText(this);
        etTitle.setHint("Например: Первый поцелуй");
        etTitle.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogLayout.addView(etTitle);

        final TextView tvPickedDate = new TextView(this);
        tvPickedDate.setText("Дата не выбрана");
        tvPickedDate.setTextColor(0xFF5C5470);
        tvPickedDate.setPadding(0, dp(16), 0, dp(4));
        tvPickedDate.setClickable(true);
        tvPickedDate.setFocusable(true);
        dialogLayout.addView(tvPickedDate);

        final String[] pickedDate = {null};

        tvPickedDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePicker = new DatePickerDialog(this,
                    (view, year, month, day) -> {
                        String formatted = String.format("%02d.%02d.%04d", day, month + 1, year);
                        pickedDate[0] = formatted;
                        tvPickedDate.setText(formatted);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            datePicker.show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Новая важная дата")
                .setView(dialogLayout)
                .setPositiveButton("Добавить", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty() || pickedDate[0] == null) {
                        Toast.makeText(this, "Укажите название и дату", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveEvent(title, pickedDate[0]);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveEvent(String title, String date) {
        String eventId = db.child("relationships").child(relationshipId)
                .child("events").push().getKey();
        if (eventId == null) return;

        java.util.Map<String, Object> event = new java.util.HashMap<>();
        event.put("title", title);
        event.put("date", date);
        event.put("addedBy", currentUserId);
        event.put("createdAt", System.currentTimeMillis());

        db.child("relationships").child(relationshipId)
                .child("events").child(eventId).setValue(event);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }

    private void setListeners() {
        btnAddEvent.setOnClickListener(v -> openAddEventDialog());

        navHome.setOnClickListener(v -> {
            startActivity(new Intent(this, RelationshipMainActivity.class));
            finish();
        });

        navMemories.setOnClickListener(v ->
                Toast.makeText(this, "Memories — скоро", Toast.LENGTH_SHORT).show());

        // Мы уже на календаре — ничего не делаем
        navCalendar.setOnClickListener(v -> {});

        navProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (relationshipId != null && eventsListener != null) {
            db.child("relationships").child(relationshipId)
                    .child("events").removeEventListener(eventsListener);
        }
    }
}




