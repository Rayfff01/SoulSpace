package com.example.soulspace;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class WaitingActivity extends AppCompatActivity {

    private LinearLayout btnCancelInvite;
    private DatabaseReference inviteRef;
    private ValueEventListener inviteListener;
    private String currentUserId;
    private InviteChecker inviteChecker;

    @Override
    protected void onResume() {
        super.onResume();
        inviteChecker = new InviteChecker();
        inviteChecker.startListening(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inviteChecker != null) {
            inviteChecker.stopListening();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        btnCancelInvite = findViewById(R.id.btnCancelInvite);

        listenForInviteResponse();

        btnCancelInvite.setOnClickListener(v -> cancelInvite());
    }

    private void listenForInviteResponse() {
        inviteRef = FirebaseDatabase.getInstance()
                .getReference("invites")
                .child(currentUserId);

        inviteListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);
                if (status == null) return;

                if (status.equals("accepted")) {
                    // Партнёр принял — переходим на главный экран
                    Toast.makeText(WaitingActivity.this,
                            "Партнёр принял приглашение!", Toast.LENGTH_SHORT).show();
                    if (status.equals("accepted")) {
                        Intent intent = new Intent(WaitingActivity.this, RelationshipMainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                } else if (status.equals("declined")) {
                    // Партнёр отклонил
                    Toast.makeText(WaitingActivity.this,
                            "Партнёр отклонил приглашение", Toast.LENGTH_SHORT).show();
                    inviteRef.removeValue();
                    Intent intent = new Intent(WaitingActivity.this, CreateRelationshipActivity.class);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("WaitingActivity", "DB error: " + error.getMessage());
            }
        };

        inviteRef.addValueEventListener(inviteListener);
    }

    private void cancelInvite() {
        inviteRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Приглашение отменено", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(WaitingActivity.this, CreateRelationshipActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Убираем listener чтобы не было утечки памяти
        if (inviteRef != null && inviteListener != null) {
            inviteRef.removeEventListener(inviteListener);
        }
    }
}