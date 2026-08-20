package com.example.soulspace;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvCoupleName, tvCoupleHandle, tvRelationshipDate, tvTogetherDuration;
    private TextView tvLogout;
    private LinearLayout navHome, navMemories, navCalendar;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUserId = user.getUid();
        UserCache.init(this);

        initViews();

        if (UserCache.hasCache()) {
            showCachedData();

            // Если кэш свежий — не идём в Firebase
            if (UserCache.isProfileCacheFresh()) {
                setListeners();
                return;
            }
        }

        loadProfileData();
        setListeners();
    }

    private void showCachedData() {
        tvCoupleName.setText(UserCache.getCoupleName());
        tvCoupleHandle.setText(UserCache.getCoupleHandle());
        tvRelationshipDate.setText(UserCache.getRelationshipDate());
        tvTogetherDuration.setText(UserCache.getDuration());
    }

    private void initViews() {
        tvCoupleName = findViewById(R.id.tvCoupleName);
        tvCoupleHandle = findViewById(R.id.tvCoupleHandle);
        tvRelationshipDate = findViewById(R.id.tvRelationshipDate);
        tvTogetherDuration = findViewById(R.id.tvTogetherDuration);
        tvLogout = findViewById(R.id.tvLogout);
        navHome = findViewById(R.id.navHome);
        navMemories = findViewById(R.id.navMemories);
        navCalendar = findViewById(R.id.navCalendar);
    }

    private void loadProfileData() {
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

                        String user1 = snapshot.child("user1").getValue(String.class);
                        String user2 = snapshot.child("user2").getValue(String.class);

                        final String[] name1 = {null};
                        final String[] name2 = {null};
                        final String[] date = {null};

                        Runnable tryShow = () -> {
                            if (name1[0] == null || name2[0] == null || date[0] == null) return;

                            String coupleName = name1[0] + " & " + name2[0];
                            String coupleHandle = "@" + name1[0].toLowerCase() + "_"
                                    + name2[0].toLowerCase() + "_soul";
                            String formattedDate = formatDate(date[0]);
                            String duration = calcDuration(date[0]);

                            tvCoupleName.setText(coupleName);
                            tvCoupleHandle.setText(coupleHandle);
                            tvRelationshipDate.setText(formattedDate);
                            tvTogetherDuration.setText(duration);

                            UserCache.saveRelationshipData(
                                    coupleName, coupleHandle, formattedDate, duration, relationshipId);
                        };

                        loadUserName(user1, n -> { name1[0] = n; tryShow.run(); });
                        loadUserName(user2, n -> { name2[0] = n; tryShow.run(); });

                        FirebaseDatabase.getInstance().getReference("invites")
                                .child(user1).child("date")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dateSnapshot) {
                                        date[0] = dateSnapshot.getValue(String.class);
                                        if (date[0] == null) date[0] = "";
                                        tryShow.run();
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError error) {
                                        date[0] = "";
                                        tryShow.run();
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    interface NameCallback {
        void onName(String name);
    }

    private void loadUserName(String uid, NameCallback callback) {
        FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("username")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            callback.onName(snapshot.getValue(String.class));
                            return;
                        }
                        FirebaseDatabase.getInstance().getReference("invites")
                                .orderByChild("fromUserId").equalTo(uid)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot snapshot) {
                                        if (snapshot.exists()) {
                                            for (DataSnapshot invite : snapshot.getChildren()) {
                                                String name = invite.child("fromUserName").getValue(String.class);
                                                if (name != null && !name.isEmpty()) {
                                                    callback.onName(name);
                                                    return;
                                                }
                                            }
                                        }
                                        callback.onName("User");
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError error) {
                                        callback.onName("User");
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onName("User");
                    }
                });
    }

    private String formatDate(String dateStr) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            Date date = input.parse(dateStr);
            SimpleDateFormat output = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
            return output.format(date);
        } catch (ParseException e) {
            return dateStr;
        }
    }

    private String calcDuration(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            Date start = sdf.parse(dateStr);
            Date now = new Date();
            long diffMs = now.getTime() - start.getTime();
            long totalDays = diffMs / (1000 * 60 * 60 * 24);
            int years = (int) (totalDays / 365);
            int months = (int) ((totalDays % 365) / 30);

            String result = "Мы вместе ";
            if (years > 0) result += years + " " + pluralYears(years) + ", ";
            result += months + " " + pluralMonths(months);
            return result;
        } catch (Exception e) {
            return "";
        }
    }

    private String pluralYears(int n) {
        if (n % 10 == 1 && n % 100 != 11) return "год";
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return "года";
        return "лет";
    }

    private String pluralMonths(int n) {
        if (n % 10 == 1 && n % 100 != 11) return "месяц";
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return "месяца";
        return "месяцев";
    }

    private void setListeners() {
        navHome.setOnClickListener(v -> {
            startActivity(new Intent(this, RelationshipMainActivity.class));
            finish();
        });

        navMemories.setOnClickListener(v ->
                Toast.makeText(this, "Memories — скоро", Toast.LENGTH_SHORT).show());

        navCalendar.setOnClickListener(v -> {
            startActivity(new Intent(this, CalendarActivity.class));
            finish();
        });

        findViewById(R.id.itemNotifications).setOnClickListener(v ->
                Toast.makeText(this, "Notifications — скоро", Toast.LENGTH_SHORT).show());

        findViewById(R.id.itemPrivacy).setOnClickListener(v ->
                Toast.makeText(this, "Privacy — скоро", Toast.LENGTH_SHORT).show());

        findViewById(R.id.itemTheme).setOnClickListener(v ->
                Toast.makeText(this, "Theme — скоро", Toast.LENGTH_SHORT).show());

        findViewById(R.id.itemAccount).setOnClickListener(v ->
                Toast.makeText(this, "Account — скоро", Toast.LENGTH_SHORT).show());

        findViewById(R.id.itemRelationshipSettings).setOnClickListener(v ->
                Toast.makeText(this, "Relationship Settings — скоро", Toast.LENGTH_SHORT).show());

        tvLogout.setOnClickListener(v -> {
            UserCache.clear();
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}