package com.training.cvmanagementbe.enums.notifications;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailTemplate {

    DRAFT_SUBMITTED("draft-submitted"),
    DRAFT_APPROVED("draft-approved"),
    DRAFT_REJECTED("draft-rejected"),
    DRAFT_CANCELLED("draft-cancelled"),
    ASSIGNMENT_REASSIGNED("assignment-reassigned"),
    CV_LIFECYCLE("cv-lifecycle"),
    CV_ROLLBACK("cv-rollback"),
    CV_UPDATE_REQUESTED("cv-update-requested"),
    PROFILE_UPDATE("profile-update"),
    PASSWORD_RESET("password-reset"),
    ACCOUNT_CREATED("account-created"),
    TEAM_HANDOVER("team-handover"),
    REMINDER_UPDATE_REQUEST("reminder-update-request"),
    REMINDER_APPROVAL_ASSIGNMENT("reminder-approval-assignment"),
    SLA_DIGEST("sla-digest");

    // File name under templates/email/ in email-service, without extension
    private final String fileName;
}
