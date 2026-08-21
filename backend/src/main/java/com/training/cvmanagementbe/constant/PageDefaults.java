package com.training.cvmanagementbe.constant;

// Pagination defaults. Strings because @RequestParam defaultValue needs a constant.
public final class PageDefaults {

    public static final String PAGE = "0";
    public static final String SIZE = "20";
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;

    public static int clampSize(int size) {
        return Math.min(Math.max(size, MIN_SIZE), MAX_SIZE);
    }

    public static int clampPage(int page) {
        return Math.max(page, 0);
    }

    private PageDefaults() {

    }
}
