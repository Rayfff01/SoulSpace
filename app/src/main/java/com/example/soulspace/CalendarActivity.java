package com.example.soulspace;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CalendarActivity extends AppCompatActivity {

    private TextView tvBack;
    private LinearLayout navHome, navMemories, navCalendar, navProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        setListeners();
    }

    private void initViews() {
        tvBack = findViewById(R.id.tvBack);
        navHome = findViewById(R.id.navHome);
        navMemories = findViewById(R.id.navMemories);
        navCalendar = findViewById(R.id.navCalendar);
        navProfile = findViewById(R.id.navProfile);
    }

    private void setListeners() {
        tvBack.setOnClickListener(v -> finish());

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
}