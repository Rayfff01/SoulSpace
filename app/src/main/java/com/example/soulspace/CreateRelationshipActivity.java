package com.example.soulspace;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class CreateRelationshipActivity extends AppCompatActivity {

    private EditText etPartner;
    private TextView tvDate;
    private LinearLayout layoutDate, layoutUploadPhoto;
    private Button btnSendInvite;
    private Uri selectedPhotoUri;
    private InviteChecker inviteChecker;
    private DatabaseReference db;
    private String currentUserId;

    private final ActivityResultLauncher<Intent> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedPhotoUri = result.getData().getData();
                    Toast.makeText(this, "Фото выбрано", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_relationship);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        db = FirebaseDatabase.getInstance().getReference();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = user.getUid();

        initViews();
        setListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        inviteChecker = new InviteChecker();
        inviteChecker.startListening(this);
        android.util.Log.d("CreateRelationship", "onResume — InviteChecker запущен");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inviteChecker != null) {
            inviteChecker.stopListening();
        }
    }

    private void initViews() {
        etPartner = findViewById(R.id.etPartner);
        tvDate = findViewById(R.id.tvDate);
        layoutDate = findViewById(R.id.layoutDate);
        layoutUploadPhoto = findViewById(R.id.layoutUploadPhoto);
        btnSendInvite = findViewById(R.id.btnSendInvite);

        etPartner.setOnClickListener(v -> {
            etPartner.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(etPartner, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void setListeners() {

        layoutDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String date = String.format("%02d.%02d.%04d",
                                selectedDay, selectedMonth + 1, selectedYear);
                        tvDate.setText(date);
                        tvDate.setTextColor(getColor(android.R.color.black));
                    }, year, month, day);

            datePicker.show();
        });

        layoutUploadPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            photoPickerLauncher.launch(intent);
        });

        btnSendInvite.setOnClickListener(v -> {
            String partner = etPartner.getText().toString().trim();
            String date = tvDate.getText().toString().trim();

            if (partner.isEmpty()) {
                etPartner.setError("Введите username или email партнёра");
                etPartner.requestFocus();
                return;
            }
            if (date.equals("дд.мм.гггг") || date.isEmpty()) {
                Toast.makeText(this, "Выберите дату начала отношений", Toast.LENGTH_SHORT).show();
                return;
            }

            sendInvite(partner, date);
        });
    }

    private void sendInvite(String partnerEmail, String date) {
        btnSendInvite.setEnabled(false);
        btnSendInvite.setText("Отправка...");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Берём имя из displayName, если нет — часть email
        String fromUserName = (user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                ? user.getDisplayName()
                : user.getEmail().split("@")[0];

        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUserId", currentUserId);
        invite.put("fromUserName", fromUserName);
        invite.put("partnerEmail", partnerEmail.toLowerCase().trim());
        invite.put("date", date);
        invite.put("status", "pending");

        db.child("invites").child(currentUserId).setValue(invite)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        android.util.Log.d("CreateRelationship", "Приглашение записано в БД");
                        btnSendInvite.setEnabled(true);
                        btnSendInvite.setText("Отправить приглашение");
                        Intent intent = new Intent(CreateRelationshipActivity.this, WaitingActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "unknown";
                        android.util.Log.e("CreateRelationship", "DB error: " + error);
                        Toast.makeText(this, "Ошибка: " + error, Toast.LENGTH_LONG).show();
                        btnSendInvite.setEnabled(true);
                        btnSendInvite.setText("Отправить приглашение");
                    }
                });
    }
}