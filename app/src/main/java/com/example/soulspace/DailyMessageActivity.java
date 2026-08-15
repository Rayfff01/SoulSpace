package com.example.soulspace;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DailyMessageActivity extends AppCompatActivity {

    private TextView tvPartnerMessage, tvPartnerMessageTime;
    private TextView tvMyMessage, tvMyMessageTime;
    private TextView tvResetTimer, tvCharCount, tvBack;
    private EditText etMessage;
    private Button btnSend;
    private LinearLayout layoutAlreadySent, layoutInput;

    // NEW: loading state views
    private View contentContainer;
    private ProgressBar progressLoading;

    private DatabaseReference db;
    private String currentUserId;
    private String partnerId;
    private ValueEventListener partnerMessageListener;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;

    // NEW: track whether each async piece has finished at least once
    private boolean myMessageReady = false;
    private boolean partnerMessageReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_message);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseDatabase.getInstance().getReference();

        initViews();
        showLoading(); // NEW: hide content until everything is ready
        loadPartnerId();
        setListeners();
    }

    private void initViews() {
        tvPartnerMessage = findViewById(R.id.tvPartnerMessage);
        tvPartnerMessageTime = findViewById(R.id.tvPartnerMessageTime);
        tvMyMessage = findViewById(R.id.tvMyMessage);
        tvMyMessageTime = findViewById(R.id.tvMyMessageTime);
        tvResetTimer = findViewById(R.id.tvResetTimer);
        tvCharCount = findViewById(R.id.tvCharCount);
        tvBack = findViewById(R.id.tvBack);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        layoutAlreadySent = findViewById(R.id.layoutAlreadySent);
        layoutInput = findViewById(R.id.layoutInput);

        // NEW — must exist in activity_daily_message.xml
        contentContainer = findViewById(R.id.contentContainer);
        progressLoading = findViewById(R.id.progressLoading);

        etMessage.setOnClickListener(v -> {
            etMessage.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
        });

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvCharCount.setText(s.length() + "/200");
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // NEW: loading state helpers
    private void showLoading() {
        contentContainer.setVisibility(View.INVISIBLE);
        progressLoading.setVisibility(View.VISIBLE);
    }

    private void showContent() {
        progressLoading.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
    }

    // NEW: called after each async piece finishes; reveals content once both are ready
    private void checkIfReadyToShow() {
        if (myMessageReady && partnerMessageReady) {
            showContent();
        }
    }

    private void loadPartnerId() {
        UserCache.init(this);
        String relationshipId = UserCache.getRelationshipId();

        if (relationshipId == null) {
            Toast.makeText(this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.child("relationships").child(relationshipId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String user1 = snapshot.child("user1").getValue(String.class);
                        String user2 = snapshot.child("user2").getValue(String.class);
                        partnerId = currentUserId.equals(user1) ? user2 : user1;

                        loadMyMessage();
                        listenPartnerMessage();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        // NEW: don't leave user stuck on loader forever
                        myMessageReady = true;
                        partnerMessageReady = true;
                        checkIfReadyToShow();
                    }
                });
    }

    private void loadMyMessage() {
        db.child("daily_messages").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            showInputMode();
                            myMessageReady = true; // NEW
                            checkIfReadyToShow();  // NEW
                            return;
                        }

                        String text = snapshot.child("text").getValue(String.class);
                        Long sentAt = snapshot.child("sentAt").getValue(Long.class);

                        if (sentAt == null) {
                            showInputMode();
                            myMessageReady = true; // NEW
                            checkIfReadyToShow();  // NEW
                            return;
                        }

                        long now = System.currentTimeMillis();
                        long dayInMs = 24 * 60 * 60 * 1000L;

                        if (now - sentAt > dayInMs) {
                            // Сутки прошли — удаляем и показываем форму
                            db.child("daily_messages").child(currentUserId).removeValue();
                            showInputMode();
                        } else {
                            // Сообщение ещё активно
                            showSentMode(text, sentAt, dayInMs - (now - sentAt));
                        }
                        myMessageReady = true; // NEW
                        checkIfReadyToShow();  // NEW
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        myMessageReady = true; // NEW
                        checkIfReadyToShow();  // NEW
                    }
                });
    }

    private void listenPartnerMessage() {
        if (partnerId == null) {
            partnerMessageReady = true; // NEW
            checkIfReadyToShow();       // NEW
            return;
        }

        partnerMessageListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    tvPartnerMessage.setText("Партнёр ещё не отправил записку сегодня");
                    tvPartnerMessageTime.setText("");
                    partnerMessageReady = true; // NEW
                    checkIfReadyToShow();       // NEW
                    return;
                }

                String text = snapshot.child("text").getValue(String.class);
                Long sentAt = snapshot.child("sentAt").getValue(Long.class);

                if (sentAt == null) {
                    partnerMessageReady = true; // NEW
                    checkIfReadyToShow();       // NEW
                    return;
                }

                long now = System.currentTimeMillis();
                long dayInMs = 24 * 60 * 60 * 1000L;

                if (now - sentAt > dayInMs) {
                    tvPartnerMessage.setText("Партнёр ещё не отправил записку сегодня");
                    tvPartnerMessageTime.setText("");
                } else {
                    tvPartnerMessage.setText(text);
                    tvPartnerMessageTime.setText("Отправлено " + formatTime(sentAt));
                }
                partnerMessageReady = true; // NEW
                checkIfReadyToShow();       // NEW
            }

            @Override
            public void onCancelled(DatabaseError error) {
                partnerMessageReady = true; // NEW
                checkIfReadyToShow();       // NEW
            }
        };

        db.child("daily_messages").child(partnerId)
                .addValueEventListener(partnerMessageListener);
    }

    private void showInputMode() {
        layoutInput.setVisibility(View.VISIBLE);
        layoutAlreadySent.setVisibility(View.GONE);
        tvResetTimer.setText("");
    }

    private void showSentMode(String text, long sentAt, long msLeft) {
        layoutInput.setVisibility(View.GONE);
        layoutAlreadySent.setVisibility(View.VISIBLE);
        tvMyMessage.setText(text);
        tvMyMessageTime.setText("Отправлено " + formatTime(sentAt));

        // Запускаем обратный отсчёт
        startCountdown(msLeft);
    }

    private void startCountdown(long msLeft) {
        timerRunnable = new Runnable() {
            long remaining = msLeft;

            @Override
            public void run() {
                if (remaining <= 0) {
                    tvResetTimer.setText("Можно отправить новую записку!");
                    db.child("daily_messages").child(currentUserId).removeValue();
                    showInputMode();
                    return;
                }

                long hours = TimeUnit.MILLISECONDS.toHours(remaining);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;
                long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60;

                tvResetTimer.setText(String.format(
                        "Новая записка через %02d:%02d:%02d", hours, minutes, seconds));

                remaining -= 1000;
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void setListeners() {
        tvBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Напиши что-нибудь", Toast.LENGTH_SHORT).show();
                return;
            }
            sendMessage(text);
        });
    }

    private void sendMessage(String text) {
        btnSend.setEnabled(false);
        btnSend.setText("Отправка...");

        Map<String, Object> message = new HashMap<>();
        message.put("text", text);
        message.put("sentAt", System.currentTimeMillis());
        message.put("fromUserId", currentUserId);

        db.child("daily_messages").child(currentUserId).setValue(message)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Записка отправлена ✉", Toast.LENGTH_SHORT).show();
                        long dayInMs = 24 * 60 * 60 * 1000L;
                        showSentMode(text, System.currentTimeMillis(), dayInMs);
                    } else {
                        btnSend.setEnabled(true);
                        btnSend.setText("Отправить записку  ✉");
                        Toast.makeText(this, "Ошибка отправки", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (partnerId != null && partnerMessageListener != null) {
            db.child("daily_messages").child(partnerId).removeEventListener(partnerMessageListener);
        }
    }
}