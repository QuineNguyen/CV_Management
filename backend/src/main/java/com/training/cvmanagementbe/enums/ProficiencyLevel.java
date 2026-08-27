package com.training.cvmanagementbe.enums;

// Skill proficiency, 1..5. Stored as a string so the scale can be relabelled without a migration.
public enum ProficiencyLevel {

    BASIC(1),
    ELEMENTARY(2),
    INTERMEDIATE(3),
    ADVANCED(4),
    EXPERT(5);

    private final int level;

    ProficiencyLevel(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
