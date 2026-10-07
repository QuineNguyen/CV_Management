package com.training.emailservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

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

    private static final String FOLDER = "email/";

    private final String fileName;

    public String path() {
        return FOLDER + fileName;
    }

    // Allowlist: A name that is not listed here is never handed to the template engine.
    public static Optional<EmailTemplate> fromFileName(String fileName) {
        return Arrays.stream(values())
                .filter(template -> template.fileName.equals(fileName))
                .findFirst();
    }
}
