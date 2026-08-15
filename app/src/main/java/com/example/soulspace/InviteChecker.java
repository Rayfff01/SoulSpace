package com.example.soulspace;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

public class InviteChecker {

    private static final String TAG = "InviteChecker";
    private Query query;
    private ValueEventListener listener;

    public void startListening(Activity activity) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "User is NULL — не авторизован");
            return;
        }

        String currentEmail = user.getEmail();
        Log.d(TAG, "Слушаем приглашения для email: " + currentEmail);

        query = FirebaseDatabase.getInstance().getReference("invites")
                .orderByChild("partnerEmail")
                .equalTo(currentEmail);

        listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.d(TAG, "onDataChange сработал. Данных: " + snapshot.getChildrenCount());

                if (!snapshot.exists()) {
                    Log.d(TAG, "Приглашений нет");
                    return;
                }

                for (DataSnapshot invite : snapshot.getChildren()) {
                    Log.d(TAG, "Найдено приглашение: " + invite.getValue());
                    String status = invite.child("status").getValue(String.class);
                    String partnerEmail = invite.child("partnerEmail").getValue(String.class);
                    Log.d(TAG, "status=" + status + " partnerEmail=" + partnerEmail);

                    if ("pending".equals(status)) {
                        String inviterUid = invite.getKey();
                        Log.d(TAG, "Открываем InviteReceivedActivity, inviterUid=" + inviterUid);

                        if (activity instanceof InviteReceivedActivity) return;

                        Intent intent = new Intent(activity, InviteReceivedActivity.class);
                        intent.putExtra("inviterUserId", inviterUid);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                        return;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Ошибка: " + error.getMessage() + " / " + error.getDetails());
            }
        };

        query.addValueEventListener(listener);
        Log.d(TAG, "Listener добавлен");
    }

    public void stopListening() {
        if (query != null && listener != null) {
            query.removeEventListener(listener);
            Log.d(TAG, "Listener удалён");
        }
    }
}