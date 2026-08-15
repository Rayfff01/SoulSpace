package com.example.soulspace;

import android.content.Context;
import android.content.SharedPreferences;

public class UserCache {

    private static final String PREFS_NAME = "soulspace_cache";
    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveRelationshipData(String coupleName, String coupleHandle,
                                            String relationshipDate, String duration,
                                            String relationshipId) {
        prefs.edit()
                .putString("couple_name", coupleName)
                .putString("couple_handle", coupleHandle)
                .putString("relationship_date", relationshipDate)
                .putString("duration", duration)
                .putString("relationship_id", relationshipId)
                .putLong("profile_updated", System.currentTimeMillis())
                .apply();
    }

    public static void saveTimerData(String years, String months, String days) {
        prefs.edit()
                .putString("timer_years", years)
                .putString("timer_months", months)
                .putString("timer_days", days)
                .putLong("timer_updated", System.currentTimeMillis())
                .apply();
    }

    public static String getCoupleName() { return prefs.getString("couple_name", null); }
    public static String getCoupleHandle() { return prefs.getString("couple_handle", null); }
    public static String getRelationshipDate() { return prefs.getString("relationship_date", null); }
    public static String getDuration() { return prefs.getString("duration", null); }
    public static String getRelationshipId() { return prefs.getString("relationship_id", null); }
    public static String getTimerYears() { return prefs.getString("timer_years", "00"); }
    public static String getTimerMonths() { return prefs.getString("timer_months", "00"); }
    public static String getTimerDays() { return prefs.getString("timer_days", "00"); }

    public static boolean hasCache() {
        return prefs.getString("couple_name", null) != null;
    }

    public static boolean hasTimerCache() {
        return !prefs.getString("timer_years", "").isEmpty();
    }

    public static boolean isProfileCacheFresh() {
        long lastUpdated = prefs.getLong("profile_updated", 0);
        long hourInMs = 60 * 60 * 1000;
        return System.currentTimeMillis() - lastUpdated < hourInMs;
    }

    public static boolean isTimerCacheFresh() {
        long lastUpdated = prefs.getLong("timer_updated", 0);
        // Таймер обновляем раз в день — данные меняются только по дням
        long dayInMs = 24 * 60 * 60 * 1000;
        return System.currentTimeMillis() - lastUpdated < dayInMs;
    }

    public static void clear() {
        prefs.edit().clear().apply();
    }
}