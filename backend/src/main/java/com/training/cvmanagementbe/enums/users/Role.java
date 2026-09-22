package com.training.cvmanagementbe.enums.users;

import java.util.Set;

public enum Role {
    ADMIN,
    HR,
    TECH_LEAD,
    EMPLOYEE;

    /*
     * Whose personnel records this role administers: creating an account, editing one and
     * reviewing a profile update request from it.
     *
     * - Admin administers everyone. HR administers tech leads and employees only,
     * never a peer, never the role above it. The cut closes two paths at once: HR
     * editing an Admin's record and HR minting an Admin account to act through. Everyone else
     * administers nobody.
     * - One method serves both user management and request routing on purpose. Two lists that
     * are meant to be the same list drift; this way "HR may not touch an Admin" cannot be true
     * in one place and false in the other.
     */
    public Set<Role> manageableRoles() {
        return switch (this) {
            case ADMIN -> Set.of(ADMIN, HR, TECH_LEAD, EMPLOYEE);
            case HR -> Set.of(TECH_LEAD, EMPLOYEE);
            default -> Set.of();
        };
    }

    /*
     * Whether a change to this user's own record goes through the approval queue.
     *
     * - False for Admin: their request could only be self-approved or deadlock outright
     * in a single-Admin system. They write directly and the audit log carries the record.
     */
    public boolean requiresProfileUpdateApproval() {
        return this != ADMIN;
    }

    public static final class Names {
        public static final String ADMIN = "ADMIN";
        public static final String HR = "HR";
        public static final String TECH_LEAD = "TECH_LEAD";
        public static final String EMPLOYEE = "EMPLOYEE";

        private Names() {}
    }
}
