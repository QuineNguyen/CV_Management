package com.training.cvmanagementbe.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Database unique index names, used to translate constraint violations
@Getter
@RequiredArgsConstructor
public enum DbConstraint {

    UK_CV_PROFILES_ACTIVE_PRIMARY("uk_cv_profiles_active_primary"),
    UK_CV_PROFILES_ACTIVE_NAME("uk_cv_profiles_active_name"),
    UK_CVS_ACTIVE_PROFILE_LANG("uk_cvs_active_profile_lang"),
    UK_CVS_ACTIVE_MASTER("uk_cvs_active_master"),
    UK_CV_DRAFTS_OPEN("uk_cv_drafts_open"),
    UK_APPROVAL_ASSIGNMENTS_ASSIGNED("uk_approval_assignments_assigned"),
    UK_UPDATE_REQUESTS_PENDING("uk_update_requests_pending"),
    UK_REMINDER_LOGS_DAILY("uk_reminder_logs_daily"),
    UK_USERS_EMAIL("uk_users_email"),
    UK_USERS_USERNAME("uk_users_username"),
    UK_CV_VERSIONS_NUMBER("uk_cv_versions_number"),
    UK_SKILLS_CODE("uk_skills_code"),
    UK_SKILLS_NAME("uk_skills_name"),
    FK_UR_CV_PROFILE("fk_ur_cv_profile"),
    UK_DEPARTMENT_CODE("uk_department_code"),
    UK_DEPARTMENT_NAME("uk_department_name"),
    UK_TEAMS_CODE("uk_teams_code"),
    UK_TEAM_MEMBERS_PAIR("uk_team_members_pair");

    private final String indexName;
}
