package com.example.soulspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.time.Period;

public class TogetherWidgetDurationTest {

    @Test
    public void formatsDaysWithinOneMonth() {
        assertEquals("12 дней", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 22)));
    }

    @Test
    public void formatsMonthsAndDays() {
        assertEquals("2 месяца · 5 дней", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 3, 15)));
    }

    @Test
    public void formatsYearsMonthsAndDays() {
        assertEquals("1 год · 2 месяца · 5 дней", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2023, 1, 10), LocalDate.of(2024, 3, 15)));
    }

    @Test
    public void hidesZeroUnits() {
        assertEquals("1 год", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2023, 1, 10), LocalDate.of(2024, 1, 10)));
        assertEquals("1 месяц", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 10)));
        assertEquals("1 день", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 11)));
    }

    @Test
    public void sameDayIsToday() {
        assertEquals("Сегодня", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 10)));
        assertEquals("Сегодня", TogetherWidgetDuration.formatDurationShort(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 10)));
        assertEquals("Сегодня", TogetherWidgetDuration.formatDurationTiny(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 10)));
    }

    @Test
    public void handlesEndOfMonthAcrossLeapFebruary() {
        assertEquals("1 месяц · 1 день", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2024, 1, 31), LocalDate.of(2024, 3, 1)));
    }

    @Test
    public void handlesLeapDayAnniversary() {
        assertEquals("4 года", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2020, 2, 29), LocalDate.of(2024, 2, 29)));
    }

    @Test
    public void handlesEndOfYear() {
        assertEquals("1 день", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2023, 12, 31), LocalDate.of(2024, 1, 1)));
    }

    @Test
    public void formatsCompactVariantsWithoutChangingPeriodMath() {
        assertEquals("1 г. · 2 мес. · 5 дн.", TogetherWidgetDuration.formatDurationShort(
                LocalDate.of(2023, 1, 10), LocalDate.of(2024, 3, 15)));
        assertEquals("1г2м5д", TogetherWidgetDuration.formatDurationTiny(
                LocalDate.of(2023, 1, 10), LocalDate.of(2024, 3, 15)));

        assertEquals("2 мес. · 5 дн.", TogetherWidgetDuration.formatDurationShort(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 3, 15)));
        assertEquals("12д", TogetherWidgetDuration.formatDurationTiny(
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 22)));
    }

    @Test
    public void formatsFutureDistanceWithoutNegativeValues() {
        assertEquals("5 дней", TogetherWidgetDuration.formatFutureDistance(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 6)));
        assertEquals("5 дн.", TogetherWidgetDuration.formatFutureDistanceShort(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 6)));
        assertEquals("5д", TogetherWidgetDuration.formatFutureDistanceTiny(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 6)));
        assertEquals("", TogetherWidgetDuration.formatFutureDistance(
                LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 1)));
        assertEquals("", TogetherWidgetDuration.formatDuration(
                LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 1)));
    }

    @Test
    public void parsesAndFormatsExistingFirebaseDate() {
        LocalDate date = TogetherWidgetDuration.parseFirebaseDate("14.02.2020");
        assertEquals(LocalDate.of(2020, 2, 14), date);
        assertEquals("14.02.2020", TogetherWidgetDuration.toFirebaseDate(date));
        assertEquals("14 февраля 2020", TogetherWidgetDuration.formatStartDate(date));
        assertNull(TogetherWidgetDuration.parseFirebaseDate("31.02.2020"));
        assertNull(TogetherWidgetDuration.parseFirebaseDate(""));
        assertNull(TogetherWidgetDuration.parseFirebaseDate(null));
    }

    @Test
    public void periodNeverExceedsElevenMonthsAfterWholeYears() {
        Period period = TogetherWidgetDuration.periodBetween(
                LocalDate.of(2020, 2, 29), LocalDate.of(2025, 2, 27));
        assertEquals(4, period.getYears());
        assertEquals(11, period.getMonths());
        assertEquals(29, period.getDays());
    }
}
