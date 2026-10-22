package com.example.soulspace;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Home screen widget that shows how long the couple has been together.
 */
public class TogetherWidgetProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent == null ? null : intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || TogetherWidgetUpdater.ACTION_REFRESH.equals(action)) {
            BroadcastReceiver.PendingResult pendingResult = goAsync();
            TogetherWidgetUpdater.refreshFromFirebase(context, pendingResult);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        TogetherWidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds);
        TogetherWidgetUpdater.scheduleNextMidnight(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
                                          AppWidgetManager appWidgetManager,
                                          int appWidgetId,
                                          Bundle newOptions) {
        TogetherWidgetUpdater.updateWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        TogetherWidgetUpdater.cancelAlarmIfUnused(context);
    }
}
