package com.example.soulspace;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Process;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DailyNoteWidgetInstrumentedTest {

    @Test
    public void widgetLayoutsInflateAsRemoteViews() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int[] layouts = {
                R.layout.widget_daily_note_compact,
                R.layout.widget_daily_note,
                R.layout.widget_daily_note_tall
        };

        for (int layout : layouts) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            View root = views.apply(context, new FrameLayout(context));
            assertNotNull(root);
            assertNotNull(root.findViewById(R.id.widget_root));
            assertNotNull(root.findViewById(R.id.widget_note_text));
            assertNotNull(root.findViewById(R.id.widget_note_meta));
        }
    }

    @Test
    public void widgetProviderIsRegisteredWithAndroid() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        List<AppWidgetProviderInfo> providers =
                manager.getInstalledProvidersForPackage(
                        context.getPackageName(), Process.myUserHandle());

        boolean found = false;
        for (AppWidgetProviderInfo provider : providers) {
            if (DailyNoteWidgetProvider.class.getName()
                    .equals(provider.provider.getClassName())) {
                found = true;
                break;
            }
        }
        assertTrue("DailyNoteWidgetProvider must be visible to the system widget picker", found);
    }
}
