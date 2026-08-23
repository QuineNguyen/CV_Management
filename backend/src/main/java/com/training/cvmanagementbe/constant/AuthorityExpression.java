package com.training.cvmanagementbe.constant;

public final class AuthorityExpression {

    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String ADMIN_OR_HR = "hasAnyRole('ADMIN', 'HR')";
    public static final String DIRECTORY_READER = "hasAnyRole('ADMIN', 'HR', 'TECH_LEAD', 'EMPLOYEE')";

    private AuthorityExpression() {}
}
