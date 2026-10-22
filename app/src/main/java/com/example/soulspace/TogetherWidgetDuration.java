package com.example.soulspace;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure calendar math and Russian formatting for the relationship widget.
 *
 * Keeping this class free of Android APIs lets the edge cases be covered by
 * fast local unit tests. Dates are always calendar dates (no time zone shift).
 */
final class TogetherWidgetDuration {

    private static final DateTimeFormatter FIREBASE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM uuuu", new Locale("ru", "RU"));

    private TogetherWidgetDuration() {
    }

    /**
     * Full calendar years, months and days between two dates.
     *
     * A month is added with LocalDate.plusMonths(), so the end of a month is
     * clamped correctly (31 January + 1 month = 29 February in a leap year).
     */
    static Period periodBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            return Period.ZERO;
        }

        int years = end.getYear() - start.getYear();
        LocalDate cursor = start.plusYears(years);
        if (cursor.isAfter(end)) {
            years--;
            cursor = start.plusYears(years);
        }

        int months = 0;
        while (months < 11) {
            LocalDate next = cursor.plusMonths(1);
            if (next.isAfter(end)) {
                break;
            }
            cursor = next;
            months++;
        }

        int days = (int) ChronoUnit.DAYS.between(cursor, end);
        return Period.of(years, months, days);
    }

    static String formatDuration(LocalDate start, LocalDate today) {
        return formatDuration(start, today, Style.FULL);
    }

    static String formatDurationShort(LocalDate start, LocalDate today) {
        return formatDuration(start, today, Style.SHORT);
    }

    static String formatDurationTiny(LocalDate start, LocalDate today) {
        return formatDuration(start, today, Style.TINY);
    }

    static String formatFutureDistance(LocalDate today, LocalDate start) {
        return formatFutureDistance(today, start, Style.FULL);
    }

    static String formatFutureDistanceShort(LocalDate today, LocalDate start) {
        return formatFutureDistance(today, start, Style.SHORT);
    }

    static String formatFutureDistanceTiny(LocalDate today, LocalDate start) {
        return formatFutureDistance(today, start, Style.TINY);
    }

    static String formatStartDate(LocalDate date) {
        return date == null ? "" : DISPLAY_DATE_FORMAT.format(date);
    }

    static String toFirebaseDate(LocalDate date) {
        return date == null ? null : FIREBASE_DATE_FORMAT.format(date);
    }

    static LocalDate parseFirebaseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), FIREBASE_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String formatDuration(LocalDate start, LocalDate today, Style style) {
        if (start == null || today == null || start.isAfter(today)) {
            return "";
        }
        return formatParts(periodBetween(start, today), style);
    }

    private static String formatFutureDistance(LocalDate today, LocalDate start, Style style) {
        if (today == null || start == null || !start.isAfter(today)) {
            return "";
        }
        return formatParts(periodBetween(today, start), style);
    }

    private static String formatParts(Period period, Style style) {
        int years = Math.max(0, period.getYears());
        int months = Math.max(0, period.getMonths());
        int days = Math.max(0, period.getDays());

        List<String> parts = new ArrayList<>(3);
        if (years > 0) {
            parts.add(formatYears(years, style));
        }
        if (months > 0) {
            parts.add(formatMonths(months, style));
        }
        if (days > 0) {
            parts.add(formatDays(days, style));
        }

        if (parts.isEmpty()) {
            return "Сегодня";
        }

        String separator = style == Style.TINY ? "" : " · ";
        return String.join(separator, parts);
    }

    private static String formatYears(int value, Style style) {
        switch (style) {
            case SHORT:
                return value + " г.";
            case TINY:
                return value + "г";
            case FULL:
            default:
                return value + " " + pluralYears(value);
        }
    }

    private static String formatMonths(int value, Style style) {
        switch (style) {
            case SHORT:
                return value + " мес.";
            case TINY:
                return value + "м";
            case FULL:
            default:
                return value + " " + pluralMonths(value);
        }
    }

    private static String formatDays(int value, Style style) {
        switch (style) {
            case SHORT:
                return value + " дн.";
            case TINY:
                return value + "д";
            case FULL:
            default:
                return value + " " + pluralDays(value);
        }
    }

    private static String pluralYears(int value) {
        return plural(value, "год", "года", "лет");
    }

    private static String pluralMonths(int value) {
        return plural(value, "месяц", "месяца", "месяцев");
    }

    private static String pluralDays(int value) {
        return plural(value, "день", "дня", "дней");
    }

    private static String plural(int value, String one, String few, String many) {
        int mod100 = value % 100;
        int mod10 = value % 10;
        if (mod10 == 1 && mod100 != 11) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
            return few;
        }
        return many;
    }

    private enum Style {
        FULL,
        SHORT,
        TINY
    }
}
