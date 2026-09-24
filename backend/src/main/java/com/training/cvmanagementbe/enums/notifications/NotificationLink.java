package com.training.cvmanagementbe.enums.notifications;

import lombok.RequiredArgsConstructor;

/**
 * Defines relative frontend (Angular) route patterns for in-app notifications and email links.
 * When a notification or email is generated, its target action link is formatted using this enum
 * and stored in the notifications.link column, allowing users to navigate directly
 * to the relevant view upon clicking.
 */
@RequiredArgsConstructor
public enum NotificationLink {

    /**
     * Route for Tech Lead, HR, or Admin to review a submitted CV draft.
     * Pattern expects draftId. Example: approvals/drafts/123/review
     */
    DRAFT_REVIEW("approvals/drafts/%s/review"),

    // Route to the approval queue listing all pending approval tasks.
    APPROVAL_QUEUE("approvals/queue"),

    /**
     * Route to view the details of a published CV.
     * Pattern expects cvId. Example: cvs/456
     */
    CV_DETAIL("cvs/%s"),

    /**
     * Route to edit a CV (e.g., when a draft is rejected and requires revision).
     * Pattern expects cvId. Example: cvs/456/edit
     */
    CV_EDIT("cvs/%s/edit"),

    // Route to the CV profiles management page.
    CV_PROFILES("cv-profiles"),

    // Route for Admin/HR to view and process employee profile update requests.
    PROFILE_UPDATE_REQUESTS("profile-update-requests"),

    // Route to the current user's profile view (e.g., after a profile update decision).
    MY_PROFILE("my-profile"),

    // Route to the account password change screen.
    CHANGE_PASSWORD("account/change-password");

    private final String pattern;

    /**
     * Formats the route pattern with dynamic arguments (e.g., IDs) to generate a relative URL.
     *
     * @param args arguments to replace format specifiers (%s) in the pattern
     * @return the formatted relative route path
     */
    public String path(Object... args) {
        return pattern.formatted(args);
    }
}

