package com.example.soulspace;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UserCache.init(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Не авторизован — на логин
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String currentEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        // Проверяем есть ли отношения
        FirebaseDatabase.getInstance().getReference("users")
                .child(currentUid)
                .child("relationshipId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            startActivity(new Intent(SplashActivity.this, RelationshipMainActivity.class));
                            finish();
                            return;
                        }

                        // Проверяем входящее приглашение
                        FirebaseDatabase.getInstance().getReference("invites")
                                .orderByChild("partnerEmail")
                                .equalTo(currentEmail)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot snapshot) {
                                        if (snapshot.exists()) {
                                            for (DataSnapshot invite : snapshot.getChildren()) {
                                                String status = invite.child("status").getValue(String.class);
                                                if ("pending".equals(status)) {
                                                    Intent intent = new Intent(SplashActivity.this, InviteReceivedActivity.class);
                                                    intent.putExtra("inviterUserId", invite.getKey());
                                                    startActivity(intent);
                                                    finish();
                                                    return;
                                                }
                                            }
                                        }
                                        startActivity(new Intent(SplashActivity.this, CreateRelationshipActivity.class));
                                        finish();
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError error) {
                                        startActivity(new Intent(SplashActivity.this, CreateRelationshipActivity.class));
                                        finish();
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                        finish();
                    }
                });
    }
}