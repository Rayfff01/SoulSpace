package com.example.soulspace;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.FirebaseDatabase;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etUsername, etPassword;
    private Button btnCreateAccount;
    private LinearLayout btnGoogle, btnApple;
    private TextView tvSignIn;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        initViews();
        setListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnApple = findViewById(R.id.btnApple);
        tvSignIn = findViewById(R.id.tvSignIn);

        etEmail.setOnClickListener(v -> {
            etEmail.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etEmail, InputMethodManager.SHOW_IMPLICIT);
        });

        etUsername.setOnClickListener(v -> {
            etUsername.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etUsername, InputMethodManager.SHOW_IMPLICIT);
        });

        etPassword.setOnClickListener(v -> {
            etPassword.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etPassword, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void setListeners() {

        btnCreateAccount.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (validateInput(email, username, password)) {
                performRegister(email, username, password);
            }
        });

        btnGoogle.setOnClickListener(v ->
                Toast.makeText(this, "Google sign-in — скоро", Toast.LENGTH_SHORT).show());

        btnApple.setOnClickListener(v ->
                Toast.makeText(this, "Apple sign-in — скоро", Toast.LENGTH_SHORT).show());

        tvSignIn.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private boolean validateInput(String email, String username, String password) {
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Введите email");
            etEmail.requestFocus();
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Некорректный email");
            etEmail.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Введите username");
            etUsername.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Введите пароль");
            etPassword.requestFocus();
            return false;
        }
        if (password.length() < 6) {
            etPassword.setError("Минимум 6 символов");
            etPassword.requestFocus();
            return false;
        }
        return true;
    }

    private void performRegister(String email, String username, String password) {
        btnCreateAccount.setEnabled(false);
        btnCreateAccount.setText("Creating...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user == null) return;

                        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                                .setDisplayName(username)
                                .build();

                        user.updateProfile(profileUpdate)
                                .addOnCompleteListener(profileTask -> {
                                    if (profileTask.isSuccessful()) {
                                        // Сохраняем username в Realtime Database
                                        FirebaseDatabase.getInstance()
                                                .getReference("users")
                                                .child(user.getUid())
                                                .child("username")
                                                .setValue(username)
                                                .addOnCompleteListener(dbTask -> {
                                                    btnCreateAccount.setEnabled(true);
                                                    btnCreateAccount.setText("CREATE ACCOUNT");

                                                    if (dbTask.isSuccessful()) {
                                                        Toast.makeText(this, "Аккаунт создан: " + username, Toast.LENGTH_SHORT).show();
                                                        startActivity(new Intent(RegisterActivity.this, CreateRelationshipActivity.class));
                                                        finish();
                                                    } else {
                                                        Toast.makeText(this, "Ошибка сохранения данных", Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    } else {
                                        btnCreateAccount.setEnabled(true);
                                        btnCreateAccount.setText("CREATE ACCOUNT");
                                        Toast.makeText(this, "Ошибка обновления профиля", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        btnCreateAccount.setEnabled(true);
                        btnCreateAccount.setText("CREATE ACCOUNT");

                        String errorMsg = "Ошибка регистрации";
                        if (task.getException() != null) {
                            String firebaseError = task.getException().getMessage();
                            if (firebaseError != null && firebaseError.contains("email address is already in use")) {
                                errorMsg = "Этот email уже зарегистрирован";
                            } else if (firebaseError != null && firebaseError.contains("badly formatted")) {
                                errorMsg = "Некорректный формат email";
                            } else {
                                errorMsg = firebaseError;
                            }
                        }
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}