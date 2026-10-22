package com.example.soulspace;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Small local cache of the relationship start date for the widget.
 *
 * Firebase remains the source of truth. The cache keeps the widget useful while
 * offline and prevents a date from a previous account from being shown after a
 * user change.
 */
final class TogetherWidgetData {

    private static final String PREFS_NAME = "soulspace_together_widget";
    private static final String KEY_OWNER_UID = "owner_uid";
    private static final String KEY_START_DATE = "start_date";

    private TogetherWidgetData() {
    }

    static void save(Context context, String ownerUid, LocalDate startDate) {
        if (ownerUid == null || ownerUid.isEmpty()) {
            return;
        }

        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_OWNER_UID, ownerUid);
        if (startDate == null) {
            editor.remove(KEY_START_DATE);
        } else {
            editor.putString(KEY_START_DATE, startDate.toString());
        }
        editor.apply();
    }

    static LocalDate load(Context context, String currentUid) {
        if (currentUid == null || currentUid.isEmpty()) {
            return null;
        }

        SharedPreferences prefs = prefs(context);
        String ownerUid = prefs.getString(KEY_OWNER_UID, null);
        if (ownerUid == null || !ownerUid.equals(currentUid)) {
            return null;
        }

        String rawDate = prefs.getString(KEY_START_DATE, null);
        if (rawDate == null || rawDate.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
