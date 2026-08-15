package com.example.soulspace;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class InviteReceivedActivity extends AppCompatActivity {

    private TextView tvInviteTitle, tvInviteDate, tvDecline;
    private Button btnAccept;

    private DatabaseReference db;
    private String currentUserId;
    private String inviterUserId;
    private ValueEventListener inviteListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invite_received);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseDatabase.getInstance().getReference();

        tvInviteTitle = findViewById(R.id.tvInviteTitle);
        tvInviteDate = findViewById(R.id.tvInviteDate);
        btnAccept = findViewById(R.id.btnAccept);
        tvDecline = findViewById(R.id.tvDecline);

        // inviterUserId передаётся через Intent
        inviterUserId = getIntent().getStringExtra("inviterUserId");

        loadInviteData();
        listenForCancellation();
        setListeners();
    }

    private void loadInviteData() {
        db.child("invites").child(inviterUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        String partnerEmail = snapshot.child("partnerEmail").getValue(String.class);
                        String date = snapshot.child("date").getValue(String.class);
                        String inviterName = snapshot.child("fromUserName").getValue(String.class);

                        if (inviterName != null) {
                            tvInviteTitle.setText(inviterName + " приглашает вас\nсоздать отношения");
                        }
                        if (date != null) {
                            tvInviteDate.setText("Начало: " + date);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        android.util.Log.e("InviteReceived", "Error: " + error.getMessage());
                    }
                });
    }

    // Следим — вдруг приглашающий отменил пока мы смотрим
    private void listenForCancellation() {
        inviteListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(InviteReceivedActivity.this,
                            "Приглашение было отменено", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };

        db.child("invites").child(inviterUserId).addValueEventListener(inviteListener);
    }

    private void setListeners() {

        btnAccept.setOnClickListener(v -> {
            btnAccept.setEnabled(false);
            btnAccept.setText("Принятие...");

            // Обновляем статус приглашения
            db.child("invites").child(inviterUserId).child("status").setValue("accepted")
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Создаём связь между пользователями
                            createRelationship();
                        } else {
                            btnAccept.setEnabled(true);
                            btnAccept.setText("ПРИНЯТЬ  →");
                            Toast.makeText(this, "Ошибка, попробуйте снова", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        tvDecline.setOnClickListener(v -> {
            db.child("invites").child(inviterUserId).child("status").setValue("declined")
                    .addOnCompleteListener(task -> {
                        Toast.makeText(this, "Приглашение отклонено", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        });
    }

    private void createRelationship() {
        String relationshipId = db.child("relationships").push().getKey();

        java.util.Map<String, Object> relationship = new java.util.HashMap<>();
        relationship.put("user1", inviterUserId);
        relationship.put("user2", currentUserId);
        relationship.put("createdAt", System.currentTimeMillis());

        db.child("relationships").child(relationshipId).setValue(relationship)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Сохраняем relationshipId обоим пользователям
                        db.child("users").child(inviterUserId).child("relationshipId").setValue(relationshipId);
                        db.child("users").child(currentUserId).child("relationshipId").setValue(relationshipId);

                        // После записи relationshipId обоим пользователям:
                        Intent intent = new Intent(InviteReceivedActivity.this, RelationshipMainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();

                        Toast.makeText(this, "Связь установлена!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null && inviteListener != null) {
            db.child("invites").child(inviterUserId).removeEventListener(inviteListener);
        }
    }
}