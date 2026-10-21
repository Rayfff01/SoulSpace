package com.example.soulspace;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small local cache of the partner's daily note for the home screen widget.
 *
 * The cache lets the widget show the last known note while the device is offline
 * or while Firebase is still loading. A cached note is only displayed for the
 * same user and only for 24 hours, matching the existing app logic.
 */
final class DailyNoteWidgetData {

    static final long NOTE_LIFETIME_MS = 24L * 60L * 60L * 1000L;

    private static final String PREFS_NAME = "soulspace_widget";
    private static final String KEY_OWNER_UID = "owner_uid";
    private static final String KEY_TEXT = "note_text";
    private static final String KEY_SENT_AT = "note_sent_at";

    private DailyNoteWidgetData() {
    }

    static final class Note {
        final String ownerUid;
        final String text;
        final long sentAt;

        Note(String ownerUid, String text, long sentAt) {
            this.ownerUid = ownerUid;
            this.text = text;
            this.sentAt = sentAt;
        }
    }

    static void save(Context context, String ownerUid, String text, long sentAt) {
        if (ownerUid == null || ownerUid.isEmpty()) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_OWNER_UID, ownerUid);
        if (text == null || text.trim().isEmpty() || sentAt <= 0) {
            editor.remove(KEY_TEXT).remove(KEY_SENT_AT).apply();
        } else {
            editor.putString(KEY_TEXT, text).putLong(KEY_SENT_AT, sentAt).apply();
        }
    }

    static Note load(Context context) {
        SharedPreferences prefs = prefs(context);
        String ownerUid = prefs.getString(KEY_OWNER_UID, null);
        if (ownerUid == null || ownerUid.isEmpty()) {
            return null;
        }
        return new Note(
                ownerUid,
                prefs.getString(KEY_TEXT, null),
                prefs.getLong(KEY_SENT_AT, 0L)
        );
    }

    /**
     * Mirrors the freshness rule used by RelationshipMainActivity: a note is
     * shown only if it belongs to the signed-in user and is no older than 24h.
     */
    static boolean isValid(Note note, String currentUid, long now) {
        if (note == null || currentUid == null || currentUid.isEmpty()) {
            return false;
        }
        if (note.ownerUid == null || !currentUid.equals(note.ownerUid)) {
            return false;
        }
        if (note.text == null || note.text.trim().isEmpty() || note.sentAt <= 0) {
            return false;
        }
        long age = now - note.sentAt;
        return age >= 0 && age <= NOTE_LIFETIME_MS;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
