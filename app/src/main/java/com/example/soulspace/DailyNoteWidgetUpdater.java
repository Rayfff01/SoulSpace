package com.example.soulspace;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
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
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);

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

        RemoteViews views = createViews(appContext, widthDp, heightDp, noteText, metaText, hasNote);
        manager.updateAppWidget(widgetId, views);
    }

    /**
     * Builds the widget content for a concrete size. Package-private so
     * instrumented tests can verify wrapping and truncation on a real device.
     */
    static RemoteViews createViews(Context context,
                                   int widthDp,
                                   int heightDp,
                                   String noteText,
                                   String metaText,
                                   boolean hasNote) {
        Context appContext = context.getApplicationContext();
        int width = widthDp > 0 ? widthDp : DailyNoteWidgetSizing.DEFAULT_WIDTH_DP;
        int height = heightDp > 0 ? heightDp : DailyNoteWidgetSizing.DEFAULT_HEIGHT_DP;

        DailyNoteWidgetSizing.Kind kind = DailyNoteWidgetSizing.chooseKind(width, height);
        LayoutSpec spec = LayoutSpec.forKind(kind);
        Sizing sizing = measure(appContext, spec, width, height, noteText);

        RemoteViews views = new RemoteViews(appContext.getPackageName(), spec.layoutRes);
        views.setTextViewText(R.id.widget_note_text, noteText);
        views.setTextColor(R.id.widget_note_text, hasNote ? COLOR_TEXT : COLOR_MUTED);
        // XML supplies safe defaults for the preview; at runtime the number of
        // lines follows the actual height reported by the host.
        views.setInt(R.id.widget_note_text, "setMaxLines", sizing.maxLines);

        views.setTextViewText(R.id.widget_note_meta, metaText);
        views.setViewVisibility(
                R.id.widget_note_meta,
                sizing.showMeta ? android.view.View.VISIBLE : android.view.View.GONE
        );

        int paddingPx = Math.round(sizing.paddingDp
                * appContext.getResources().getDisplayMetrics().density);
        views.setViewPadding(
                R.id.widget_card, paddingPx, paddingPx, paddingPx, paddingPx);

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(appContext));
        return views;
    }

    private static Sizing measure(Context context,
                                  LayoutSpec spec,
                                  int widthDp,
                                  int heightDp,
                                  CharSequence text) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density;
        int paddingDp = spec.paddingFor(heightDp);

        TextPaint textPaint = newPaint(spToPx(spec.textSizeSp, metrics));
        // getFontSpacing() already includes the font leading, so it is a
        // conservative line height. The card may then still leave a couple of
        // dp unused, which is cheaper than clipping the last line.
        float textLineHeightPx = textPaint.getFontSpacing()
                * spec.lineSpacingMultiplier;

        float headerHeightPx =
                newPaint(spToPx(spec.headerTextSizeSp, metrics)).getFontSpacing();
        float metaHeightPx =
                newPaint(spToPx(spec.metaTextSizeSp, metrics)).getFontSpacing();

        float fixedHeightPx = 2f * paddingDp * density
                + headerHeightPx
                + spec.textTopMarginDp * density
                + spec.dividerBlockDp * density;
        float heightPx = heightDp * density;
        float safetyPx = 2f * density;

        float availableWithoutMeta = Math.max(0f, heightPx - fixedHeightPx - safetyPx);
        float availableWithMeta = Math.max(
                0f,
                availableWithoutMeta
                        - metaHeightPx
                        - spec.metaTopMarginDp * density
        );

        int linesWithoutMeta = DailyNoteWidgetSizing.maxLines(
                availableWithoutMeta, textLineHeightPx);
        int linesWithMeta = spec.supportsMeta
                ? DailyNoteWidgetSizing.maxLines(availableWithMeta, textLineHeightPx)
                : 0;

        int measuredLines = countLines(text, textPaint, spec, widthDp, paddingDp, metrics);
        // Keep the meta row only while the whole note fits with it. A longer
        // note takes the freed line instead of being cut off one line earlier.
        boolean showMeta = spec.supportsMeta
                && availableWithMeta >= textLineHeightPx
                && measuredLines <= linesWithMeta;

        int maxLines = showMeta ? linesWithMeta : linesWithoutMeta;
        return new Sizing(maxLines, showMeta, paddingDp);
    }

    private static int countLines(CharSequence text,
                                  TextPaint paint,
                                  LayoutSpec spec,
                                  int widthDp,
                                  int paddingDp,
                                  DisplayMetrics metrics) {
        if (text == null || text.length() == 0) {
            return 1;
        }
        int contentWidthPx = Math.max(
                1,
                Math.round((widthDp - 2f * paddingDp) * metrics.density)
        );
        StaticLayout layout = StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, contentWidthPx)
                .setLineSpacing(0f, spec.lineSpacingMultiplier)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .build();
        return Math.max(1, layout.getLineCount());
    }

    private static TextPaint newPaint(float textSizePx) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSizePx);
        paint.setTypeface(Typeface.DEFAULT);
        return paint;
    }

    private static float spToPx(float sp, DisplayMetrics metrics) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics);
    }

    private static final class Sizing {
        final int maxLines;
        final boolean showMeta;
        final int paddingDp;

        Sizing(int maxLines, boolean showMeta, int paddingDp) {
            this.maxLines = maxLines;
            this.showMeta = showMeta;
            this.paddingDp = paddingDp;
        }
    }

    /**
     * Visual parameters that mirror the three XML layouts. Keeping them next to
     * the measuring code makes the line math and the inflated views agree.
     */
    private static final class LayoutSpec {
        final DailyNoteWidgetSizing.Kind kind;
        final int layoutRes;
        final float textSizeSp;
        final float lineSpacingMultiplier;
        final float headerTextSizeSp;
        final float metaTextSizeSp;
        final int paddingDp;
        final int textTopMarginDp;
        final int dividerBlockDp;
        final int metaTopMarginDp;
        final boolean supportsMeta;

        LayoutSpec(DailyNoteWidgetSizing.Kind kind,
                   int layoutRes,
                   float textSizeSp,
                   float lineSpacingMultiplier,
                   float headerTextSizeSp,
                   float metaTextSizeSp,
                   int paddingDp,
                   int textTopMarginDp,
                   int dividerBlockDp,
                   int metaTopMarginDp,
                   boolean supportsMeta) {
            this.kind = kind;
            this.layoutRes = layoutRes;
            this.textSizeSp = textSizeSp;
            this.lineSpacingMultiplier = lineSpacingMultiplier;
            this.headerTextSizeSp = headerTextSizeSp;
            this.metaTextSizeSp = metaTextSizeSp;
            this.paddingDp = paddingDp;
            this.textTopMarginDp = textTopMarginDp;
            this.dividerBlockDp = dividerBlockDp;
            this.metaTopMarginDp = metaTopMarginDp;
            this.supportsMeta = supportsMeta;
        }

        static LayoutSpec forKind(DailyNoteWidgetSizing.Kind kind) {
            switch (kind) {
                case TALL:
                    return new LayoutSpec(
                            kind,
                            R.layout.widget_daily_note_tall,
                            17f,
                            1.35f,
                            16f,
                            12f,
                            22,
                            16,
                            15,
                            10,
                            true
                    );
                case NORMAL:
                    return new LayoutSpec(
                            kind,
                            R.layout.widget_daily_note,
                            15f,
                            1.3f,
                            14f,
                            11f,
                            18,
                            12,
                            11,
                            6,
                            true
                    );
                case COMPACT:
                default:
                    return new LayoutSpec(
                            kind,
                            R.layout.widget_daily_note_compact,
                            13f,
                            1.25f,
                            12f,
                            10f,
                            14,
                            6,
                            0,
                            4,
                            false
                    );
            }
        }

        int paddingFor(int heightDp) {
            if (kind == DailyNoteWidgetSizing.Kind.COMPACT && heightDp < 90) {
                return 10;
            }
            if (kind == DailyNoteWidgetSizing.Kind.NORMAL && heightDp < 150) {
                return 14;
            }
            return paddingDp;
        }
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
