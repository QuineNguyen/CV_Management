package com.training.cvmanagementbe.constant;

public final class ApiPath {

    // Department paths
    public static final String DEPARTMENTS = "/departments";
    public static final String TREE = "/tree";
    public static final String BY_ID = "/{id}";
    public static final String MOVE = "/{id}/move";

    // Team and user paths
    public static final String TEAMS = "/teams";
    public static final String USERS = "/users";
    public static final String MEMBERS = "/{id}/members";
    public static final String MEMBER_BY_USER = "/{id}/members/{userId}";
    public static final String TECH_LEADS = "/tech-leads";
    public static final String DEACTIVATE = "/{id}/deactivate";
    public static final String ACTIVATE = "/{id}/activate";

    // CV profiles
    public static final String EMPLOYEES = "/employees";
    public static final String PROFILES = "/profiles";
    public static final String PROFILES_BY_EMPLOYEE = "/{employeeId}/profiles";
    public static final String PROFILE_ENSURE = "/{employeeId}/profiles/ensure";
    public static final String PROFILE_TEAM_OPTIONS = "/{employeeId}/profiles/team-options";
    public static final String SET_PRIMARY = "/{id}/set-primary";

    private ApiPath() {}
}
