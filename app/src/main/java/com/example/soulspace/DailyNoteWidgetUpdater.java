package com.example.soulspace;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Draws the daily note widget and refreshes it from the same Firebase data the
 * app already uses (daily_messages/{partnerId}).
 *
 * The widget is self-sufficient: it reads Firebase when the system asks for an
 * update, saves the result to a small local cache, and shows the cache while
 * offline. Existing app screens call {@link #onNoteLoaded} to update the widget
 * immediately when they receive a fresh note in real time.
 */
final class DailyNoteWidgetUpdater {

    static final String ACTION_REFRESH =
            "com.example.soulspace.action.REFRESH_DAILY_NOTE_WIDGET";

    private static final int REFRESH_TIMEOUT_MS = 8000;
    private static final int REQUEST_OPEN = 1001;
    private static final int REQUEST_EXPIRY = 1002;
    private static final long EXPIRY_GRACE_MS = 30L * 1000L;

    private static final int COLOR_TEXT = Color.parseColor("#2D2D2D");
    private static final int COLOR_MUTED = Color.parseColor("#8A8A8A");

    private DailyNoteWidgetUpdater() {
    }

    /** Called by the app when its realtime listener receives a valid note. */
    static void onNoteLoaded(Context context, String ownerUid, String text, long sentAt) {
        Context appContext = context.getApplicationContext();
        DailyNoteWidgetData.save(appContext, ownerUid, text, sentAt);
        refreshAllFromCache(appContext);
    }

    static void updateWidgets(Context context, AppWidgetManager manager, int[] widgetIds) {
        Context appContext = context.getApplicationContext();
        for (int widgetId : widgetIds) {
            updateWidget(appContext, manager, widgetId);
        }
    }

    static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        Context appContext = context.getApplicationContext();
        String currentUid = currentUid();
        DailyNoteWidgetData.Note note = DailyNoteWidgetData.load(appContext);
        boolean hasNote = DailyNoteWidgetData.isValid(
                note, currentUid, System.currentTimeMillis());

        Bundle options = manager.getAppWidgetOptions(widgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);

        boolean compact = minWidth < 200 || minHeight < 130;
        boolean tall = !compact && minWidth >= 250 && minHeight >= 250;
        int layout = compact
                ? R.layout.widget_daily_note_compact
                : (tall ? R.layout.widget_daily_note_tall : R.layout.widget_daily_note);

        RemoteViews views = new RemoteViews(appContext.getPackageName(), layout);

        String noteText;
        String metaText;
        if (hasNote) {
            noteText = note.text;
            metaText = appContext.getString(
                    R.string.widget_daily_note_from_partner,
                    formatTime(note.sentAt)
            );
        } else {
            noteText = currentUid == null
                    ? appContext.getString(R.string.widget_daily_note_signed_out)
                    : appContext.getString(R.string.widget_daily_note_empty);
            metaText = appContext.getString(R.string.widget_daily_note_hint);
        }

        views.setTextViewText(R.id.widget_note_text, noteText);
        views.setTextColor(R.id.widget_note_text, hasNote ? COLOR_TEXT : COLOR_MUTED);

        views.setTextViewText(R.id.widget_note_meta, metaText);
        boolean showMeta = !compact && minHeight >= 170;
        views.setViewVisibility(
                R.id.widget_note_meta,
                showMeta ? android.view.View.VISIBLE : android.view.View.GONE
        );

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(appContext));
        manager.updateAppWidget(widgetId, views);
    }

    /** Re-renders every widget from the local cache and re-arms the expiry alarm. */
    static void refreshAllFromCache(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        if (manager == null) {
            return;
        }
        ComponentName provider = new ComponentName(appContext, DailyNoteWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(provider);
        updateWidgets(appContext, manager, widgetIds);
        updateExpiryAlarm(appContext);
    }

    /**
     * Loads the partner's note from Firebase. Runs inside the AppWidget update
     * broadcast via goAsync(), with a timeout so the broadcast is always
     * finished even when the network is unavailable.
     */
    static void refreshFromFirebase(Context context, BroadcastReceiver.PendingResult pendingResult) {
        final Context appContext = context.getApplicationContext();
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] timeoutRef = new Runnable[1];

        final Runnable finish = () -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (timeoutRef[0] != null) {
                handler.removeCallbacks(timeoutRef[0]);
            }
            refreshAllFromCache(appContext);
            if (pendingResult != null) {
                pendingResult.finish();
            }
        };

        timeoutRef[0] = finish;
        handler.postDelayed(finish, REFRESH_TIMEOUT_MS);

        final String uid;
        final DatabaseReference database;
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                finish.run();
                return;
            }
            uid = user.getUid();
            database = FirebaseDatabase.getInstance().getReference();
        } catch (Exception exception) {
            finish.run();
            return;
        }

        database.child("users").child(uid).child("relationshipId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String relationshipId = snapshot.getValue(String.class);
                        if (relationshipId == null || relationshipId.isEmpty()) {
                            DailyNoteWidgetData.save(appContext, uid, null, 0L);
                            finish.run();
                            return;
                        }
                        loadPartnerAndNote(appContext, database, uid, relationshipId, finish);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        finish.run();
                    }
                });
    }

    private static void loadPartnerAndNote(Context context,
                                           DatabaseReference database,
                                           String uid,
                                           String relationshipId,
                                           Runnable finish) {
        database.child("relationships").child(relationshipId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String user1 = snapshot.child("user1").getValue(String.class);
                        String user2 = snapshot.child("user2").getValue(String.class);
                        String partnerId = uid.equals(user1) ? user2 : user1;
                        if (partnerId == null || partnerId.isEmpty()) {
                            DailyNoteWidgetData.save(context, uid, null, 0L);
                            finish.run();
                            return;
                        }
                        loadNote(context, database, uid, partnerId, finish);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        finish.run();
                    }
                });
    }

    private static void loadNote(Context context,
                                 DatabaseReference database,
                                 String uid,
                                 String partnerId,
                                 Runnable finish) {
        database.child("daily_messages").child(partnerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String text = snapshot.child("text").getValue(String.class);
                        Long sentAt = snapshot.child("sentAt").getValue(Long.class);
                        long now = System.currentTimeMillis();

                        boolean valid = text != null
                                && !text.trim().isEmpty()
                                && sentAt != null
                                && sentAt > 0
                                && now >= sentAt
                                && now - sentAt <= DailyNoteWidgetData.NOTE_LIFETIME_MS;

                        if (valid) {
                            DailyNoteWidgetData.save(context, uid, text, sentAt);
                        } else {
                            DailyNoteWidgetData.save(context, uid, null, 0L);
                        }
                        finish.run();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        finish.run();
                    }
                });
    }

    private static void updateExpiryAlarm(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        if (manager == null) {
            return;
        }
        ComponentName provider = new ComponentName(appContext, DailyNoteWidgetProvider.class);
        if (manager.getAppWidgetIds(provider).length == 0) {
            cancelExpiryAlarm(appContext);
            return;
        }

        String currentUid = currentUid();
        DailyNoteWidgetData.Note note = DailyNoteWidgetData.load(appContext);
        if (!DailyNoteWidgetData.isValid(note, currentUid, System.currentTimeMillis())) {
            cancelExpiryAlarm(appContext);
            return;
        }

        long triggerAt = note.sentAt
                + DailyNoteWidgetData.NOTE_LIFETIME_MS
                + EXPIRY_GRACE_MS;
        if (triggerAt <= System.currentTimeMillis()) {
            cancelExpiryAlarm(appContext);
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        // Inexact alarm: works without SCHEDULE_EXACT_ALARM and survives Doze
        // as far as the OS allows. The 30-minute widget update is the fallback.
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                triggerAt,
                expiryPendingIntent(appContext)
        );
    }

    static void cancelExpiryAlarmIfUnused(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
        if (manager == null) {
            return;
        }
        ComponentName provider =
                new ComponentName(context.getApplicationContext(), DailyNoteWidgetProvider.class);
        if (manager.getAppWidgetIds(provider).length == 0) {
            cancelExpiryAlarm(context);
        }
    }

    private static void cancelExpiryAlarm(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(expiryPendingIntent(context));
        }
    }

    private static PendingIntent expiryPendingIntent(Context context) {
        Intent intent = new Intent(context, DailyNoteWidgetProvider.class);
        intent.setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_EXPIRY,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent openAppPendingIntent(Context context) {
        Intent intent;
        UserCache.init(context);
        if (currentUid() != null && UserCache.getRelationshipId() != null) {
            intent = new Intent(context, DailyMessageActivity.class);
        } else {
            // Not signed in yet (or no cached relationship): let the existing
            // splash routing decide where to go. This avoids opening an
            // activity that expects an authenticated user.
            intent = new Intent(context, SplashActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static String currentUid() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            return user == null ? null : user.getUid();
        } catch (Exception exception) {
            return null;
        }
    }

    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }
}
