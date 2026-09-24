package com.training.cvmanagementbe.enums.notifications;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
 * Shared value set of notifications.type and email_logs.event_type.
 * Stored by constant name, so a rename needs a migration of both CHECK constraints.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationEventType {

    DRAFT_SUBMITTED(EmailTemplate.DRAFT_SUBMITTED),
    // Level 1 passed: HR receives a review request, same template as a submit
    DRAFT_APPROVED_LEVEL_1(EmailTemplate.DRAFT_SUBMITTED),
    DRAFT_APPROVED_LEVEL_2(EmailTemplate.DRAFT_APPROVED),
    DRAFT_REJECTED(EmailTemplate.DRAFT_REJECTED),
    DRAFT_CANCELLED(EmailTemplate.DRAFT_CANCELLED),
    ASSIGNMENT_REASSIGNED(EmailTemplate.ASSIGNMENT_REASSIGNED),
    CV_DELETED(EmailTemplate.CV_LIFECYCLE),
    CV_RESTORED(EmailTemplate.CV_LIFECYCLE),
    CV_PROFILE_DELETED(EmailTemplate.CV_LIFECYCLE),
    PROFILE_UPDATE_SUBMITTED(EmailTemplate.PROFILE_UPDATE),
    PROFILE_UPDATE_DECIDED(EmailTemplate.PROFILE_UPDATE),
    PASSWORD_RESET(EmailTemplate.PASSWORD_RESET);

    private final EmailTemplate template;
}