package com.example.soulspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TogetherWidgetInstrumentedTest {

    private static final String FULL = "1 год · 3 месяца · 12 дней";
    private static final String COMPACT = "1 г. · 3 мес. · 12 дн.";
    private static final String TINY = "1г3м12д";

    @Test
    public void widgetLayoutInflatesAsRemoteViews() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteViews views = new RemoteViews(
                context.getPackageName(), R.layout.widget_together);
        View root = views.apply(context, new FrameLayout(context));

        assertNotNull(root);
        assertNotNull(root.findViewById(R.id.widget_together_root));
        assertNotNull(root.findViewById(R.id.widget_together_count));
        assertEquals(2, ((ViewGroup) root).getChildCount());
    }

    @Test
    public void widgetProviderIsRegisteredWithOneCellHeight() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        List<AppWidgetProviderInfo> providers =
                manager.getInstalledProvidersForPackage(
                        context.getPackageName(), Process.myUserHandle());

        boolean found = false;
        for (AppWidgetProviderInfo provider : providers) {
            if (TogetherWidgetProvider.class.getName()
                    .equals(provider.provider.getClassName())) {
                found = true;
                assertTrue(provider.minWidth > 0);
                assertTrue(provider.minHeight > 0);
                assertTrue("Widget must stay within a one-cell height",
                        provider.minHeight <= px(context, 44));
                assertEquals(1, provider.targetCellHeight);
                assertEquals(AppWidgetProviderInfo.RESIZE_HORIZONTAL, provider.resizeMode);
                break;
            }
        }
        assertTrue("TogetherWidgetProvider must be visible to the system widget picker", found);
    }

    @Test
    public void configurationActivityIsRegistered() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setPackage(context.getPackageName());
        List<ResolveInfo> activities = context.getPackageManager()
                .queryIntentActivities(intent, 0);

        boolean found = false;
        for (ResolveInfo activity : activities) {
            if (activity.activityInfo != null
                    && TogetherWidgetConfigureActivity.class.getName()
                    .equals(activity.activityInfo.name)) {
                found = true;
                break;
            }
        }
        assertTrue("The widget configuration activity must be launchable by the launcher",
                found);
    }

    @Test
    public void remoteViewsUseFullFormatWhenThereIsRoom() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View root = apply(context, 400, 40, FULL, COMPACT, TINY);
        TextView count = root.findViewById(R.id.widget_together_count);

        assertEquals(FULL, count.getText().toString());
        assertEquals(1, count.getMaxLines());
        assertEquals(1, count.getLineCount());
        assertNoEllipsis(count);
    }

    @Test
    public void remoteViewsUseTinyFormatAtMinimumWidth() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View root = apply(context, 110, 40, FULL, COMPACT, TINY);
        TextView count = root.findViewById(R.id.widget_together_count);

        assertEquals(TINY, count.getText().toString());
        assertEquals(1, count.getLineCount());
        assertNoEllipsis(count);
    }

    @Test
    public void contentStaysOnOneLineInsideWidgetBoundsAtSupportedWidths() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int[] widths = {110, 180, 250, 300, 400};

        for (int width : widths) {
            View root = apply(context, width, 40, FULL, COMPACT, TINY);
            TextView count = root.findViewById(R.id.widget_together_count);

            assertEquals("Widget grew past one cell at " + width + "dp",
                    px(context, 40), root.getHeight());
            assertEquals(1, count.getMaxLines());
            assertEquals("Text wrapped at " + width + "dp", 1, count.getLineCount());
            assertTrue("Count overflowed horizontally at " + width + "dp",
                    count.getRight() <= root.getWidth() + 1);
            assertNoEllipsis(count);
        }
    }

    private static void assertNoEllipsis(TextView view) {
        assertNotNull(view.getLayout());
        assertEquals("Text was ellipsized: " + view.getText(),
                0, view.getLayout().getEllipsisCount(0));
    }

    private static View apply(Context context,
                              int widthDp,
                              int heightDp,
                              String full,
                              String compact,
                              String tiny) {
        RemoteViews views = TogetherWidgetUpdater.createViews(
                context, widthDp, full, compact, tiny);
        FrameLayout parent = new FrameLayout(context);
        View root = views.apply(context, parent);

        int widthPx = px(context, widthDp);
        int heightPx = px(context, heightDp);
        root.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        );
        root.layout(0, 0, widthPx, heightPx);
        return root;
    }

    private static int px(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
