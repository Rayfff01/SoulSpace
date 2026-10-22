package com.example.soulspace;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders the relationship widget and keeps its local date cache in sync with
 * the existing Firebase field at invites/{user1}/date.
 */
final class TogetherWidgetUpdater {

    static final String ACTION_REFRESH =
            "com.example.soulspace.action.REFRESH_TOGETHER_WIDGET";

    private static final int REFRESH_TIMEOUT_MS = 8_000;
    private static final int REQUEST_ALARM = 2_401;
    private static final int REQUEST_CONFIGURE_BASE = 2_500;
    private static final int REQUEST_OPEN_APP = 2_402;
    private static final long MIDNIGHT_GRACE_MS = 2L * 60L * 1000L;

    private static final int DEFAULT_WIDTH_DP = 180;
    private static final float COUNT_TEXT_SIZE_SP = 16f;
    private static final float HEART_TEXT_SIZE_SP = 14f;
    private static final int HORIZONTAL_PADDING_DP = 8;
    private static final int HEART_MARGIN_DP = 3;
    private static final int WIDTH_SAFETY_DP = 1;

    private TogetherWidgetUpdater() {
    }

    static void updateAllFromCache(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        if (manager == null) {
            return;
        }

        int[] widgetIds = manager.getAppWidgetIds(
                new ComponentName(appContext, TogetherWidgetProvider.class));
        updateWidgets(appContext, manager, widgetIds);

        if (widgetIds.length > 0) {
            scheduleNextMidnight(appContext);
        } else {
            cancelAlarm(appContext);
        }
    }

    static void updateWidgets(Context context, AppWidgetManager manager, int[] widgetIds) {
        if (manager == null || widgetIds == null) {
            return;
        }
        for (int widgetId : widgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        if (manager == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }

        Context appContext = context.getApplicationContext();
        String uid = currentUid();
        LocalDate startDate = TogetherWidgetData.load(appContext, uid);
        LocalDate today = LocalDate.now();

        TextVariants count = buildTextVariants(appContext, uid, startDate, today);
        int widthDp = widgetWidth(manager, widgetId);
        RemoteViews views = createViews(appContext, widthDp, count);

        boolean needsConfiguration = uid != null
                && (startDate == null || startDate.isAfter(today));
        views.setOnClickPendingIntent(R.id.widget_together_root,
                needsConfiguration
                        ? configurePendingIntent(appContext, widgetId)
                        : openAppPendingIntent(appContext));

        manager.updateAppWidget(widgetId, views);
    }

    static RemoteViews createViews(Context context, int widthDp, TextVariants variants) {
        String count = chooseCountText(context, widthDp, variants);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_together);
        views.setTextViewText(R.id.widget_together_count, count);
        views.setInt(R.id.widget_together_count, "setMaxLines", 1);
        return views;
    }

    static RemoteViews createViews(Context context, int widthDp,
                                   String full, String compact, String tiny) {
        return createViews(context, widthDp, new TextVariants(full, compact, tiny));
    }

    private static TextVariants buildTextVariants(Context context,
                                                  String uid,
                                                  LocalDate startDate,
                                                  LocalDate today) {
        if (uid == null || uid.isEmpty()) {
            String signedOut = context.getString(R.string.widget_together_signed_out);
            return new TextVariants(
                    signedOut,
                    signedOut,
                    context.getString(R.string.widget_together_signed_out_tiny));
        }

        if (startDate == null) {
            return new TextVariants(
                    context.getString(R.string.widget_together_no_date),
                    context.getString(R.string.widget_together_no_date_short),
                    context.getString(R.string.widget_together_no_date_tiny));
        }

        if (startDate.isAfter(today)) {
            return new TextVariants(
                    context.getString(
                            R.string.widget_together_future,
                            TogetherWidgetDuration.formatFutureDistance(today, startDate)),
                    context.getString(
                            R.string.widget_together_future_short,
                            TogetherWidgetDuration.formatFutureDistanceShort(today, startDate)),
                    context.getString(
                            R.string.widget_together_future_tiny,
                            TogetherWidgetDuration.formatFutureDistanceTiny(today, startDate)));
        }

        return new TextVariants(
                TogetherWidgetDuration.formatDuration(startDate, today),
                TogetherWidgetDuration.formatDurationShort(startDate, today),
                TogetherWidgetDuration.formatDurationTiny(startDate, today));
    }

    private static String chooseCountText(Context context, int widthDp, TextVariants variants) {
        if (variants == null) {
            return "";
        }

        float availableWidthPx = availableTextWidthPx(context, widthDp);
        TextPaint paint = countPaint(context);

        if (fits(paint, variants.full, availableWidthPx)) {
            return variants.full;
        }
        if (fits(paint, variants.compact, availableWidthPx)) {
            return variants.compact;
        }
        return variants.tiny;
    }

    private static boolean fits(TextPaint paint, String text, float availableWidthPx) {
        return text != null
                && !text.isEmpty()
                && paint.measureText(text) <= availableWidthPx;
    }

    private static float availableTextWidthPx(Context context, int widthDp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int safeWidthDp = widthDp > 0 ? widthDp : DEFAULT_WIDTH_DP;
        float density = metrics.density;

        return Math.max(0f,
                safeWidthDp * density
                        - 2f * HORIZONTAL_PADDING_DP * density
                        - HEART_MARGIN_DP * density
                        - heartWidthPx(context)
                        - WIDTH_SAFETY_DP * density);
    }

    private static float heartWidthPx(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(HEART_TEXT_SIZE_SP * metrics.scaledDensity);
        return paint.measureText(context.getString(R.string.widget_together_heart));
    }

    private static TextPaint countPaint(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setTextSize(COUNT_TEXT_SIZE_SP * metrics.scaledDensity);
        return paint;
    }

    static void refreshFromFirebase(Context context,
                                    BroadcastReceiver.PendingResult pendingResult) {
        final Context appContext = context.getApplicationContext();
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] timeout = new Runnable[1];

        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                if (timeout[0] != null) {
                    handler.removeCallbacks(timeout[0]);
                }
                updateAllFromCache(appContext);
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        };
        timeout[0] = finish;
        handler.postDelayed(finish, REFRESH_TIMEOUT_MS);

        FirebaseUser user;
        DatabaseReference database;
        try {
            user = FirebaseAuth.getInstance().getCurrentUser();
            database = FirebaseDatabase.getInstance().getReference();
        } catch (RuntimeException exception) {
            finish.run();
            return;
        }

        if (user == null) {
            finish.run();
            return;
        }

        final String uid = user.getUid();
        database.child("users").child(uid).child("relationshipId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (finished.get()) {
                            return;
                        }

                        String relationshipId = snapshot.getValue(String.class);
                        if (relationshipId == null || relationshipId.isEmpty()) {
                            TogetherWidgetData.save(appContext, uid, null);
                            finish.run();
                            return;
                        }
                        loadRelationshipDate(appContext, database, uid, relationshipId,
                                finished, finish);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        finish.run();
                    }
                });
    }

    static void scheduleNextMidnight(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        long nextMidnight = LocalDate.now(zone)
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli() + MIDNIGHT_GRACE_MS;
        if (nextMidnight <= now) {
            nextMidnight += 24L * 60L * 60L * 1000L;
        }

        // Inexact on purpose: no exact-alarm permission, and Android may batch
        // this after midnight. The widget also has a periodic system update.
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                nextMidnight,
                refreshPendingIntent(context));
    }

    static void cancelAlarmIfUnused(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return;
        }
        int[] widgetIds = manager.getAppWidgetIds(
                new ComponentName(context, TogetherWidgetProvider.class));
        if (widgetIds.length == 0) {
            cancelAlarm(context);
        }
    }

    private static void cancelAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(refreshPendingIntent(context));
        }
    }

    private static PendingIntent refreshPendingIntent(Context context) {
        Intent intent = new Intent(context, TogetherWidgetProvider.class)
                .setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent configurePendingIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, TogetherWidgetConfigureActivity.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(TogetherWidgetConfigureActivity.EXTRA_APPWIDGET_ID, widgetId);
        return PendingIntent.getActivity(
                context,
                REQUEST_CONFIGURE_BASE + widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAppPendingIntent(Context context) {
        Intent intent = new Intent(context, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void loadRelationshipDate(Context context,
                                             DatabaseReference database,
                                             String uid,
                                             String relationshipId,
                                             AtomicBoolean finished,
                                             Runnable finish) {
        database.child("relationships").child(relationshipId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (finished.get()) {
                            return;
                        }

                        String user1 = snapshot.child("user1").getValue(String.class);
                        if (user1 == null || user1.isEmpty()) {
                            TogetherWidgetData.save(context, uid, null);
                            finish.run();
                            return;
                        }

                        database.child("invites").child(user1).child("date")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dateSnapshot) {
                                        if (finished.get()) {
                                            return;
                                        }
                                        LocalDate startDate =
                                                TogetherWidgetDuration.parseFirebaseDate(
                                                        dateSnapshot.getValue(String.class));
                                        TogetherWidgetData.save(context, uid, startDate);
                                        finish.run();
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError error) {
                                        finish.run();
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        finish.run();
                    }
                });
    }

    private static String currentUid() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            return user == null ? null : user.getUid();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int widgetWidth(AppWidgetManager manager, int widgetId) {
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        return width > 0 ? width : DEFAULT_WIDTH_DP;
    }

    static final class TextVariants {
        final String full;
        final String compact;
        final String tiny;

        TextVariants(String full, String compact, String tiny) {
            this.full = full;
            this.compact = compact;
            this.tiny = tiny;
        }
    }
}
