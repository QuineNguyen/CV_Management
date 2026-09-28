package com.training.cvmanagementbe.enums.cvs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UpdateRequestSortField {

    CREATED_AT("createdAt"),
    DEADLINE("deadline"),
    // Goes through the read-only employee association of UpdateRequest
    EMPLOYEE_NAME("employee.fullName");

    private final String property;
}
