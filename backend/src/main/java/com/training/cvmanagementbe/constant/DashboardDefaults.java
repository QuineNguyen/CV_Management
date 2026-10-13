package com.training.cvmanagementbe.constant;

// Fixed sizes and windows of the dashboard widgets.
public final class DashboardDefaults {

    // Rows per "most urgent" widget; the full list lives on its own paged screen
    public static final int WIDGET_ROW_LIMIT = 10;

    // Same default as reminder threshold N; moves to system_configs in Phase 9
    public static final int APPROACHING_DAYS = 3;

    private DashboardDefaults() {}
}
