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
    PROFILE_UPDATE("profile-update"),
    PASSWORD_RESET("password-reset");

    // File name under templates/email/ in email-service, without extension
    private final String fileName;
}
