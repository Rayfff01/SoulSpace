package com.example.soulspace;

/**
 * Pure sizing rules for the daily note widget.
 *
 * Kept free of Android views so the breakpoints and line math can be covered by
 * fast local unit tests. The updater supplies real text metrics on top of these
 * rules.
 */
final class DailyNoteWidgetSizing {

    enum Kind {
        COMPACT,
        NORMAL,
        TALL
    }

    /** Matches the provider defaults for hosts that do not report widget options. */
    static final int DEFAULT_WIDTH_DP = 110;
    static final int DEFAULT_HEIGHT_DP = 110;

    private static final int NARROW_WIDTH_DP = 200;
    private static final int SHORT_HEIGHT_DP = 130;
    private static final int TALL_SIZE_DP = 250;

    private DailyNoteWidgetSizing() {
    }

    static Kind chooseKind(int widthDp, int heightDp) {
        int width = widthDp > 0 ? widthDp : DEFAULT_WIDTH_DP;
        int height = heightDp > 0 ? heightDp : DEFAULT_HEIGHT_DP;

        if (width < NARROW_WIDTH_DP || height < SHORT_HEIGHT_DP) {
            return Kind.COMPACT;
        }
        if (width >= TALL_SIZE_DP && height >= TALL_SIZE_DP) {
            return Kind.TALL;
        }
        return Kind.NORMAL;
    }

    /**
     * How many whole text lines fit into the available height. Never returns
     * less than one line: a widget with too little room shows a truncated line
     * instead of an empty body.
     */
    static int maxLines(float availableHeightPx, float lineHeightPx) {
        if (availableHeightPx <= 0f || lineHeightPx <= 0f) {
            return 1;
        }
        int lines = (int) Math.floor(availableHeightPx / lineHeightPx);
        return Math.max(1, lines);
    }
}
