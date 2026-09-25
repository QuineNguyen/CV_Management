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
    PROFILE_UPDATE("profile-update"),
    PASSWORD_RESET("password-reset");

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
