package com.example.soulspace;

import android.app.DatePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.time.LocalDate;

/**
 * Standard AppWidget configuration screen. It is opened by the launcher on the
 * first widget add and can also be opened from the widget for a date change.
 */
public class TogetherWidgetConfigureActivity extends AppCompatActivity {

    static final String EXTRA_APPWIDGET_ID = "appWidgetId";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private TextView tvDate;
    private TextView tvMessage;
    private TextView tvLater;
    private LinearLayout layoutDate;
    private Button btnSave;
    private ProgressBar progress;

    private DatabaseReference database;
    private String uid;
    private String relationshipId;
    private String user1;
    private LocalDate selectedDate;
    private boolean available;
    private boolean saving;
    private boolean dateChosenByUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent sourceIntent = getIntent();
        appWidgetId = sourceIntent == null
                ? AppWidgetManager.INVALID_APPWIDGET_ID
                : sourceIntent.getIntExtra(EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED);

        setContentView(R.layout.activity_together_widget_configure);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        UserCache.init(this);
        bindViews();

        database = FirebaseDatabase.getInstance().getReference();
        loadAccount();
    }

    private void bindViews() {
        tvDate = findViewById(R.id.tvConfigureDate);
        tvMessage = findViewById(R.id.tvConfigureMessage);
        tvLater = findViewById(R.id.tvConfigureLater);
        layoutDate = findViewById(R.id.layoutConfigureDate);
        btnSave = findViewById(R.id.btnConfigureSave);
        progress = findViewById(R.id.progressConfigure);

        layoutDate.setOnClickListener(view -> showDatePicker());
        tvLater.setOnClickListener(view -> finishWithWidget());
        btnSave.setOnClickListener(view -> saveDate());
    }

    private void loadAccount() {
        setLoading(true);

        FirebaseUser user;
        try {
            user = FirebaseAuth.getInstance().getCurrentUser();
        } catch (RuntimeException exception) {
            user = null;
        }

        if (user == null) {
            showUnavailable(R.string.widget_together_configure_login);
            return;
        }

        uid = user.getUid();
        String cachedRelationshipId = UserCache.getRelationshipId();
        if (cachedRelationshipId != null && !cachedRelationshipId.isEmpty()) {
            relationshipId = cachedRelationshipId;
            loadRelationship();
            return;
        }

        database.child("users").child(uid).child("relationshipId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        relationshipId = snapshot.getValue(String.class);
                        if (relationshipId == null || relationshipId.isEmpty()) {
                            showUnavailable(R.string.widget_together_configure_no_relationship);
                            return;
                        }
                        loadRelationship();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showUnavailable(R.string.widget_together_configure_load_error);
                    }
                });
    }

    private void loadRelationship() {
        database.child("relationships").child(relationshipId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        user1 = snapshot.child("user1").getValue(String.class);
                        if (user1 == null || user1.isEmpty()) {
                            showUnavailable(R.string.widget_together_configure_no_relationship);
                            return;
                        }
                        loadExistingDate();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showUnavailable(R.string.widget_together_configure_load_error);
                    }
                });
    }

    private void loadExistingDate() {
        LocalDate cachedDate = TogetherWidgetData.load(this, uid);
        if (cachedDate != null) {
            selectedDate = cachedDate;
            updateDateText();
        }

        database.child("invites").child(user1).child("date")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!dateChosenByUser) {
                            LocalDate firebaseDate = TogetherWidgetDuration.parseFirebaseDate(
                                    snapshot.getValue(String.class));
                            if (firebaseDate != null) {
                                selectedDate = firebaseDate;
                                TogetherWidgetData.save(
                                        TogetherWidgetConfigureActivity.this, uid, firebaseDate);
                                updateDateText();
                            }
                        }
                        showAvailable();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showAvailable();
                    }
                });
    }

    private void showDatePicker() {
        if (!available) {
            return;
        }

        LocalDate initial = selectedDate != null ? selectedDate : LocalDate.now();
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth);
                    dateChosenByUser = true;
                    updateDateText();
                    updateFutureHint();
                },
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        ).show();
    }

    private void saveDate() {
        if (!available || saving) {
            return;
        }
        if (selectedDate == null) {
            showMessage(R.string.widget_together_configure_select_date);
            return;
        }
        if (uid == null || user1 == null) {
            showUnavailable(R.string.widget_together_configure_load_error);
            return;
        }

        saving = true;
        btnSave.setEnabled(false);
        layoutDate.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        tvMessage.setVisibility(View.GONE);

        database.child("invites").child(user1).child("date")
                .setValue(TogetherWidgetDuration.toFirebaseDate(selectedDate))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        TogetherWidgetData.save(
                                getApplicationContext(), uid, selectedDate);
                        UserCache.invalidateRelationshipCache();
                        TogetherWidgetUpdater.updateAllFromCache(getApplicationContext());
                    }

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    saving = false;
                    progress.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        Toast.makeText(this, R.string.widget_together_configure_saved,
                                Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK, resultIntent());
                        finish();
                    } else {
                        btnSave.setEnabled(true);
                        layoutDate.setEnabled(true);
                        showMessage(R.string.widget_together_configure_error);
                    }
                });
    }

    private void finishWithWidget() {
        TogetherWidgetUpdater.updateAllFromCache(this);
        setResult(RESULT_OK, resultIntent());
        finish();
    }

    private void openApp() {
        TogetherWidgetUpdater.updateAllFromCache(this);
        setResult(RESULT_OK, resultIntent());
        startActivity(new Intent(this, SplashActivity.class));
        finish();
    }

    private Intent resultIntent() {
        Intent result = new Intent();
        result.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);
        return result;
    }

    private void setLoading(boolean loading) {
        available = false;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        layoutDate.setEnabled(!loading);
        layoutDate.setAlpha(loading ? 0.5f : 1f);
        btnSave.setEnabled(!loading);
        if (loading) {
            tvMessage.setVisibility(View.GONE);
        }
    }

    private void showAvailable() {
        available = true;
        progress.setVisibility(View.GONE);
        layoutDate.setEnabled(true);
        layoutDate.setAlpha(1f);
        btnSave.setText(R.string.widget_together_configure_save);
        btnSave.setEnabled(true);
        btnSave.setOnClickListener(view -> saveDate());
        updateDateText();
        updateFutureHint();
    }

    private void showUnavailable(int messageRes) {
        available = false;
        saving = false;
        progress.setVisibility(View.GONE);
        layoutDate.setEnabled(false);
        layoutDate.setAlpha(0.5f);
        btnSave.setText(R.string.widget_together_open_app);
        btnSave.setEnabled(true);
        btnSave.setOnClickListener(view -> openApp());
        showMessage(messageRes);
    }

    private void updateDateText() {
        if (selectedDate == null) {
            tvDate.setText(R.string.widget_together_configure_date_hint);
            tvDate.setTextColor(0xFF9A9A9A);
        } else {
            tvDate.setText(TogetherWidgetDuration.formatStartDate(selectedDate));
            tvDate.setTextColor(0xFF2D2D2D);
        }
    }

    private void updateFutureHint() {
        if (available && selectedDate != null
                && selectedDate.isAfter(LocalDate.now())) {
            showMessage(R.string.widget_together_configure_future);
        } else if (available) {
            tvMessage.setVisibility(View.GONE);
        }
    }

    private void showMessage(int messageRes) {
        tvMessage.setText(messageRes);
        tvMessage.setVisibility(View.VISIBLE);
    }
}
