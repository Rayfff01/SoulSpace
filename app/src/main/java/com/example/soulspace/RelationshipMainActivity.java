package com.example.soulspace;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
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
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RelationshipMainActivity extends AppCompatActivity {

    private TextView tvYears, tvMonths, tvDays;
    private LinearLayout navHome, navMemories, navCalendar, navProfile;
    private String currentUserId;
    private String partnerId;
    private DatabaseReference db;

    private void loadPartnerId() {
        db = FirebaseDatabase.getInstance().getReference();
        String relationshipId = UserCache.getRelationshipId();
        if (relationshipId == null) return;

        db.child("relationships").child(relationshipId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String user1 = snapshot.child("user1").getValue(String.class);
                        String user2 = snapshot.child("user2").getValue(String.class);
                        partnerId = currentUserId.equals(user1) ? user2 : user1;
                        loadDailyMessage();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_relationship_main);

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

        initViews();

        if (UserCache.hasTimerCache()) {
            tvYears.setText(UserCache.getTimerYears());
            tvMonths.setText(UserCache.getTimerMonths());
            tvDays.setText(UserCache.getTimerDays());

            // Если кэш свежий — не идём в Firebase
            if (UserCache.isTimerCacheFresh()) {
                setListeners();
                return;
            }
        }

        loadRelationshipData();
        setListeners();
    }

    private void initViews() {
        tvYears = findViewById(R.id.tvYears);
        tvMonths = findViewById(R.id.tvMonths);
        tvDays = findViewById(R.id.tvDays);
        navHome = findViewById(R.id.navHome);
        navMemories = findViewById(R.id.navMemories);
        navCalendar = findViewById(R.id.navCalendar);
        navProfile = findViewById(R.id.navProfile);
    }

    private void setListeners() {
        navHome.setOnClickListener(v -> {});

        navMemories.setOnClickListener(v ->
                Toast.makeText(this, "Memories — скоро", Toast.LENGTH_SHORT).show());

        navCalendar.setOnClickListener(v ->
                Toast.makeText(this, "Calendar — скоро", Toast.LENGTH_SHORT).show());

        navProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });

        findViewById(R.id.btnSendMessage).setOnClickListener(v -> {
            startActivity(new Intent(this, DailyMessageActivity.class));
        });
    }
    private void loadDailyMessage() {
        if (partnerId == null) return;

        db.child("daily_messages").child(partnerId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        TextView tvMsg = findViewById(R.id.tvDailyMessage);
                        if (!snapshot.exists()) {
                            tvMsg.setText("Сообщений пока нет. Отправь записку партнёру!");
                            return;
                        }
                        String text = snapshot.child("text").getValue(String.class);
                        Long sentAt = snapshot.child("sentAt").getValue(Long.class);
                        if (sentAt == null) return;

                        long dayInMs = 24 * 60 * 60 * 1000L;
                        if (System.currentTimeMillis() - sentAt > dayInMs) {
                            tvMsg.setText("Сообщений пока нет. Отправь записку партнёру!");
                        } else {
                            tvMsg.setText(text);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void loadRelationshipData() {
        FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserId)
                .child("relationshipId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;
                        loadRelationship(snapshot.getValue(String.class));
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void loadRelationship(String relationshipId) {
        FirebaseDatabase.getInstance().getReference("relationships")
                .child(relationshipId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        String inviterUid = snapshot.child("user1").getValue(String.class);
                        if (inviterUid == null) return;

                        FirebaseDatabase.getInstance().getReference("invites")
                                .child(inviterUid).child("date")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dateSnapshot) {
                                        String date = dateSnapshot.getValue(String.class);
                                        if (date != null) calculateTimer(date);
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError error) {}
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void calculateTimer(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            Date startDate = sdf.parse(dateStr);
            Date now = new Date();

            long diffMs = now.getTime() - startDate.getTime();
            long totalDays = TimeUnit.MILLISECONDS.toDays(diffMs);

            int years = (int) (totalDays / 365);
            int months = (int) ((totalDays % 365) / 30);
            int days = (int) (totalDays % 30);

            String y = String.format("%02d", years);
            String m = String.format("%02d", months);
            String d = String.format("%02d", days);

            tvYears.setText(y);
            tvMonths.setText(m);
            tvDays.setText(d);

            UserCache.saveTimerData(y, m, d);

        } catch (Exception e) {
            android.util.Log.e("RelationshipMain", "Date parse error: " + e.getMessage());
        }
    }
}