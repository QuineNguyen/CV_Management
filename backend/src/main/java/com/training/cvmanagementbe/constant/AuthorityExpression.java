package com.training.cvmanagementbe.constant;

public final class AuthorityExpression {

    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String ADMIN_OR_HR = "hasAnyRole('ADMIN', 'HR')";

    private AuthorityExpression() {}
}
