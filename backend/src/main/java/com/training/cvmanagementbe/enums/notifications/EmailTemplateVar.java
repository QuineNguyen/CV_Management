package com.training.cvmanagementbe.enums.notifications;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Variable names the Thymeleaf templates in email-service read.
@Getter
@RequiredArgsConstructor
public enum EmailTemplateVar {

    EMPLOYEE_NAME("employeeName"),
    PROFILE_NAME("profileName"),
    LANGUAGE("language"),
    LEVEL_LABEL("levelLabel"),
    LEVEL1_SKIPPED("level1Skipped"),
    FORWARDED("forwarded"),
    VERSION_NUMBER("versionNumber"),
    REASON("reason"),
    REVIEWER_NAME("reviewerName"),
    OWNER_VIEW("ownerView"),
    INCOMING("incoming"),
    OTHER_REVIEWER_NAME("otherReviewerName"),
    ACTOR_NAME("actorName"),
    ACTION_LABEL("actionLabel"),
    TARGET_LABEL("targetLabel"),
    CHANGED_FIELDS("changedFields"),
    DECIDED("decided"),
    APPROVED("approved"),
    TEMPORARY_PASSWORD("temporaryPassword");

    private final String key;
}
