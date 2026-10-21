package com.example.soulspace;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DailyNoteWidgetDataTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String UID = "user-1";

    @Test
    public void noteWithinTwentyFourHoursIsValid() {
        DailyNoteWidgetData.Note note =
                new DailyNoteWidgetData.Note(UID, "Тёплые слова", NOW - 1000L);
        assertTrue(DailyNoteWidgetData.isValid(note, UID, NOW));
    }

    @Test
    public void noteExactlyTwentyFourHoursOldIsValid() {
        DailyNoteWidgetData.Note note = new DailyNoteWidgetData.Note(
                UID, "Тёплые слова", NOW - DailyNoteWidgetData.NOTE_LIFETIME_MS);
        assertTrue(DailyNoteWidgetData.isValid(note, UID, NOW));
    }

    @Test
    public void expiredNoteIsInvalid() {
        DailyNoteWidgetData.Note note = new DailyNoteWidgetData.Note(
                UID, "Тёплые слова", NOW - DailyNoteWidgetData.NOTE_LIFETIME_MS - 1L);
        assertFalse(DailyNoteWidgetData.isValid(note, UID, NOW));
    }

    @Test
    public void emptyNoteIsInvalid() {
        assertFalse(DailyNoteWidgetData.isValid(
                new DailyNoteWidgetData.Note(UID, "   ", NOW), UID, NOW));
        assertFalse(DailyNoteWidgetData.isValid(
                new DailyNoteWidgetData.Note(UID, null, NOW), UID, NOW));
    }

    @Test
    public void noteOfAnotherUserIsInvalid() {
        DailyNoteWidgetData.Note note =
                new DailyNoteWidgetData.Note("another-user", "Тёплые слова", NOW);
        assertFalse(DailyNoteWidgetData.isValid(note, UID, NOW));
    }

    @Test
    public void missingUserOrNoteIsInvalid() {
        DailyNoteWidgetData.Note note =
                new DailyNoteWidgetData.Note(UID, "Тёплые слова", NOW);
        assertFalse(DailyNoteWidgetData.isValid(note, null, NOW));
        assertFalse(DailyNoteWidgetData.isValid(null, UID, NOW));
    }

    @Test
    public void futureNoteIsInvalid() {
        DailyNoteWidgetData.Note note =
                new DailyNoteWidgetData.Note(UID, "Тёплые слова", NOW + 1000L);
        assertFalse(DailyNoteWidgetData.isValid(note, UID, NOW));
    }
}
