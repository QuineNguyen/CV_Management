package com.training.cvmanagementbe.enums;

public enum Role {
    ADMIN,
    HR,
    TECH_LEAD,
    EMPLOYEE;

    public static final class Names {
        public static final String ADMIN = "ADMIN";
        public static final String HR = "HR";
        public static final String TECH_LEAD = "TECH_LEAD";
        public static final String EMPLOYEE = "EMPLOYEE";

        private Names() {}
    }
}
