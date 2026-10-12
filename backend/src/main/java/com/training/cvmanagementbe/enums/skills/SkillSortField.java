package com.training.cvmanagementbe.enums.skills;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Sortable columns of the skill catalogue screen; usage cannot is computed, so not sortable
@Getter
@RequiredArgsConstructor
public enum SkillSortField {
    DISPLAY_ORDER("displayOrder"),
    NAME("name"),
    CODE("code");

    private final String property;
}
