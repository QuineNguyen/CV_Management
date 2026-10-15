package com.training.cvmanagementbe.constant;

/*
 * Allowed ranges for the editable system settings. Constants, so Bean Validation annotations
 * can use them.
 */
public final class SystemConfigLimits {

    public static final int MIN_HOUR = 0;
    public static final int MAX_HOUR = 23;

    // A day count of 0 would make every assignment overdue on creation
    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 30;

    private SystemConfigLimits() {}
}
