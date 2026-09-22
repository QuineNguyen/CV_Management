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
    public static final String PROFILES = "/cv-profiles";
    public static final String PROFILES_BY_EMPLOYEE = "/{employeeId}/cv-profiles";
    public static final String PROFILE_ENSURE = "/{employeeId}/cv-profiles/ensure";
    public static final String PROFILE_TEAM_OPTIONS = "/{employeeId}/cv-profiles/team-options";
    public static final String SET_PRIMARY = "/{id}/set-primary";

    // CVs
    public static final String CVS_BY_PROFILE = "/{profileId}/cvs";
    public static final String CVS = "/cvs";
    public static final String CV_CONTENT = "/{id}/content";
    public static final String RESTORE = "/{id}/restore";
    public static final String DELETED = "/deleted";
    public static final String VERSIONS = "/{id}/versions";

    // Images
    public static final String IMAGES = "/images";

    // My profile (self-service)
    public static final String ME = "/me";
    public static final String MY_PROFILE = "/profile";
    public static final String MY_PROFILE_UPDATE_REQUEST = "/profile-update-request";
    public static final String MY_PROFILE_UPDATE_REQUEST_LATEST = "/profile-update-request/latest";

    // Profile update requests
    public static final String PROFILE_UPDATE_REQUESTS = "/profile-update-requests";
    public static final String APPROVE = "/{id}/approve";
    public static final String REJECT = "/{id}/reject";
    public static final String PENDING_COUNT = "/pending-count";

    // Approval queue
    public static final String APPROVALS = "/approvals";
    public static final String APPROVAL_QUEUE = "/queue";
    public static final String DRAFT_SUBMIT = "/drafts/{draftId}/submit";
    public static final String DRAFT_REVIEW = "/drafts/{draftId}/review";
    public static final String DRAFT_APPROVE = "/drafts/{draftId}/approve";
    public static final String DRAFT_REJECT = "/drafts/{draftId}/reject";
    public static final String DRAFT_RESUBMIT = "/drafts/{draftId}/resubmit";
    public static final String COMMENT_REPLY = "/comments/{commentId}/reply";

    private ApiPath() {}
}
