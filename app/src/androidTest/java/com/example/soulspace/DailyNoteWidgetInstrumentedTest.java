package com.example.soulspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Process;
import android.text.Layout;
import android.util.TypedValue;
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
            assertNotNull(root.findViewById(R.id.widget_card));
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

    @Test
    public void shortNoteKeepsCardCompactAndLongNoteUsesAvailableHeight() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int widthDp = 250;
        int heightDp = 250;

        View shortRoot = apply(context, widthDp, heightDp,
                "Люблю тебя", "От партнёра · 12:00", true);
        ViewGroup shortCard = shortRoot.findViewById(R.id.widget_card);
        TextView shortNote = shortRoot.findViewById(R.id.widget_note_text);
        TextView shortMeta = shortRoot.findViewById(R.id.widget_note_meta);

        assertEquals(1, shortNote.getLineCount());
        assertEquals(View.VISIBLE, shortMeta.getVisibility());
        assertTrue("A one-line note should leave the card compact",
                shortCard.getHeight() < px(context, heightDp));

        String longText = repeat(
                "Сегодня был очень длинный день, но каждая мысль всё равно " +
                        "возвращалась к тебе. ",
                14);
        View longRoot = apply(context, widthDp, heightDp,
                longText, "От партнёра · 12:00", true);
        ViewGroup longCard = longRoot.findViewById(R.id.widget_card);
        TextView longNote = longRoot.findViewById(R.id.widget_note_text);
        TextView longMeta = longRoot.findViewById(R.id.widget_note_meta);

        assertTrue("A long note gets the meta line back for text",
                longNote.getMaxLines() > shortNote.getMaxLines());
        assertEquals(View.GONE, longMeta.getVisibility());
        assertTrue("The card must stay inside the host bounds",
                longCard.getHeight() <= px(context, heightDp));
        assertTrue(longNote.getLineCount() <= longNote.getMaxLines());

        Layout layout = longNote.getLayout();
        assertNotNull(layout);
        assertTrue("A note that does not fit must show an ellipsis",
                layout.getEllipsisCount(layout.getLineCount() - 1) > 0);
    }

    @Test
    public void switchingFromLongBackToShortRestoresCompactHeight() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int widthDp = 250;
        int heightDp = 250;
        String shortText = "Люблю тебя";
        String longText = repeat(
                "Сегодня был очень длинный день, но каждая мысль всё равно " +
                        "возвращалась к тебе. ", 14);

        int shortHeight = cardHeight(apply(context, widthDp, heightDp,
                shortText, "От партнёра · 12:00", true));
        int longHeight = cardHeight(apply(context, widthDp, heightDp,
                longText, "От партнёра · 12:00", true));
        int shortAgainHeight = cardHeight(apply(context, widthDp, heightDp,
                shortText, "От партнёра · 12:00", true));

        assertTrue("A long note must need more room than a short one",
                longHeight > shortHeight);
        assertEquals("Returning to a short note must not keep the old height",
                shortHeight, shortAgainHeight);
    }

    @Test
    public void contentNeverExceedsWidgetBoundsAtAnySize() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int[][] sizes = {
                {110, 70},
                {110, 110},
                {250, 110},
                {250, 180},
                {250, 250},
                {400, 400}
        };
        String longText = repeat(
                "Очень длинная записка с кириллицей, эмодзи ❤️ и переносами.\n",
                20);

        for (int[] size : sizes) {
            for (String text : new String[]{"Коротко", longText, ""}) {
                View root = apply(context, size[0], size[1],
                        text, "От партнёра · 12:00", !text.isEmpty());
                ViewGroup card = root.findViewById(R.id.widget_card);
                TextView note = root.findViewById(R.id.widget_note_text);

                assertTrue(
                        "Card overflow at " + size[0] + "x" + size[1],
                        card.getHeight() <= px(context, size[1]) + 1);
                assertTrue(note.getMaxLines() >= 1);
                assertTrue(note.getLineCount() <= note.getMaxLines());
            }
        }
    }

    @Test
    public void textStyleFollowsWidgetSize() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        TextView compact = noteView(apply(context, 110, 250, "Текст", "meta", true));
        TextView normal = noteView(apply(context, 250, 180, "Текст", "meta", true));
        TextView tall = noteView(apply(context, 250, 250, "Текст", "meta", true));

        assertEquals(sp(context, 13), compact.getTextSize(), 0.5f);
        assertEquals(sp(context, 15), normal.getTextSize(), 0.5f);
        assertEquals(sp(context, 17), tall.getTextSize(), 0.5f);
    }

    @Test
    public void cyrillicEmojiNewlinesAndLongWordsRenderSafely() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String text = "Привет, любимая 😊\n\n" +
                "Сегодня был очень длинный день, но я всё время думал о тебе ❤️\n" +
                "ОченьДлинноеСловоБезПробеловКотороеДолжноПереноситьсяКорректно";

        View root = apply(context, 250, 250, text, "От партнёра · 12:00", true);
        TextView note = noteView(root);
        ViewGroup card = root.findViewById(R.id.widget_card);

        assertEquals(text, note.getText().toString());
        assertTrue(note.getLineCount() >= 2);
        assertTrue(note.getLineCount() <= note.getMaxLines());
        assertTrue(card.getHeight() <= px(context, 250));
    }

    private static View apply(Context context,
                              int widthDp,
                              int heightDp,
                              String noteText,
                              String metaText,
                              boolean hasNote) {
        RemoteViews views = DailyNoteWidgetUpdater.createViews(
                context, widthDp, heightDp, noteText, metaText, hasNote);
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

    private static TextView noteView(View root) {
        return root.findViewById(R.id.widget_note_text);
    }

    private static int cardHeight(View root) {
        ViewGroup card = root.findViewById(R.id.widget_card);
        return card.getHeight();
    }

    private static int px(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static float sp(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
