package com.example.soulspace;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DailyNoteWidgetSizingTest {

    @Test
    public void narrowWidgetUsesCompactLayout() {
        assertEquals(
                DailyNoteWidgetSizing.Kind.COMPACT,
                DailyNoteWidgetSizing.chooseKind(110, 400)
        );
    }

    @Test
    public void shortWidgetUsesCompactLayout() {
        assertEquals(
                DailyNoteWidgetSizing.Kind.COMPACT,
                DailyNoteWidgetSizing.chooseKind(400, 110)
        );
    }

    @Test
    public void mediumWidgetUsesNormalLayout() {
        assertEquals(
                DailyNoteWidgetSizing.Kind.NORMAL,
                DailyNoteWidgetSizing.chooseKind(250, 180)
        );
    }

    @Test
    public void largeWidgetUsesTallLayout() {
        assertEquals(
                DailyNoteWidgetSizing.Kind.TALL,
                DailyNoteWidgetSizing.chooseKind(250, 250)
        );
    }

    @Test
    public void missingSizeFallsBackToDefaultCompactLayout() {
        assertEquals(
                DailyNoteWidgetSizing.Kind.COMPACT,
                DailyNoteWidgetSizing.chooseKind(0, 0)
        );
    }

    @Test
    public void maxLinesNeverDropsBelowOne() {
        assertEquals(1, DailyNoteWidgetSizing.maxLines(0f, 50f));
        assertEquals(1, DailyNoteWidgetSizing.maxLines(-10f, 50f));
        assertEquals(1, DailyNoteWidgetSizing.maxLines(30f, 50f));
        assertEquals(1, DailyNoteWidgetSizing.maxLines(50f, 0f));
    }

    @Test
    public void maxLinesCountsOnlyWholeLines() {
        assertEquals(1, DailyNoteWidgetSizing.maxLines(50f, 50f));
        assertEquals(2, DailyNoteWidgetSizing.maxLines(149f, 50f));
        assertEquals(3, DailyNoteWidgetSizing.maxLines(150f, 50f));
        assertEquals(10, DailyNoteWidgetSizing.maxLines(500f, 50f));
    }
}
