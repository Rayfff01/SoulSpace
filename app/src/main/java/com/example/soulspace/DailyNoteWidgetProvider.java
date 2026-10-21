package com.example.soulspace;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Home screen widget that shows the partner's current daily note.
 *
 * Nothing here replaces an in-app screen: this is a real Android AppWidget.
 */
public class DailyNoteWidgetProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent == null ? null : intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || DailyNoteWidgetUpdater.ACTION_REFRESH.equals(action)) {
            BroadcastReceiver.PendingResult pendingResult = goAsync();
            DailyNoteWidgetUpdater.refreshFromFirebase(context, pendingResult);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Show the cached note immediately; Firebase refresh continues in onReceive.
        DailyNoteWidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
                                          AppWidgetManager appWidgetManager,
                                          int appWidgetId,
                                          Bundle newOptions) {
        DailyNoteWidgetUpdater.updateWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        DailyNoteWidgetUpdater.cancelExpiryAlarmIfUnused(context);
    }
}
