package com.training.cvmanagementbe.enums;

import java.util.Arrays;

public enum ApprovalLevel {

    // Tech Lead - technical review. The only level that can be skipped.
    LEVEL_1(1, Role.TECH_LEAD),

    // HR - format and spelling only. Never skipped; falls through to Admin.
    LEVEL_2(2, Role.HR);

    private final int value;
    private final Role reviewerRole;

    ApprovalLevel(int value, Role reviewerRole) {
        this.value = value;
        this.reviewerRole = reviewerRole;
    }

    public int value() {
        return value;
    }

    public Role reviewerRole() {
        return reviewerRole;
    }

    public static ApprovalLevel of(int value) {
        return Arrays.stream(values())
                .filter(level -> level.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown approval level: " + value));
    }
}
