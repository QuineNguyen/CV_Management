package com.training.cvmanagementbe.enums;

/**
 * Error contract shared by the API and the frontend.
 *
 * <p>Every error response carries one of these codes. Codes are <b>semantic</b>: they name the
 * situation the caller ran into, not an internal rule number. A code like
 * {@code PENDING_REQUEST_EXISTS} tells an integrator what happened without making them look
 * anything up; an opaque identifier would not.
 *
 * <p>The message here is a fallback for logs and for direct API consumers. The frontend renders
 * its own copy keyed by {@link #code()}, so changing wording here never breaks the UI — but
 * <b>renaming a code is a breaking contract change</b> and needs review from both sides.
 */
public enum ErrorCode {

    // ---------- Identity and organisation ----------
    DUPLICATE_EMAIL("Email is already registered"),
    DUPLICATE_USERNAME("Username is already taken"),
    TEAM_MEMBERSHIP_REQUIRED("Every employee must belong to at least one team, with exactly one marked as primary"),
    LAST_ACTIVE_ADMIN("The system must keep at least one active administrator"),
    TECH_LEAD_STILL_ASSIGNED("This person still leads a team; assign a replacement lead first"),

    // ---------- CV profiles and CVs ----------
    PROFILE_NAME_TAKEN("Another active profile of this employee already uses that name"),
    PRIMARY_PROFILE_EXISTS("This employee already has a primary profile"),
    CV_LANGUAGE_EXISTS("An active CV already exists for this profile in that language"),
    MASTER_CV_EXISTS("This profile already has a master CV"),
    OPEN_DRAFT_EXISTS("This CV already has an open draft"),
    VERSION_NUMBER_TAKEN("That version number is already used by this CV"),
    NOT_CV_OWNER("Only the CV owner can edit its content"),
    DRAFT_CONTENT_LOCKED("The draft is read-only while it is awaiting approval"),

    // ---------- Approval ----------
    APPROVAL_ALREADY_ASSIGNED("This draft already has an open approval assignment"),

    // ---------- Update requests ----------
    PENDING_REQUEST_EXISTS("A pending update request already exists for this employee, profile and language"),
    CV_PROFILE_MISMATCH("The linked CV does not belong to the profile this request targets"),

    // ---------- Catalogue ----------
    DUPLICATE_SKILL_CODE("Another skill already uses that code"),
    DUPLICATE_SKILL_NAME("Another skill already uses that name"),

    // ---------- Reminders ----------
    REMINDER_ALREADY_SENT("A reminder for this target and recipient was already sent today"),

    // ---------- Generic ----------
    /** Returned as 403 whenever the caller's role does not cover the requested data. */
    OUT_OF_SCOPE("You do not have access to this data"),
    /** Returned as 409 when a compare-and-set update matched zero rows. */
    STALE_STATE("Someone changed this item first; reload and try again"),
    VALIDATION_FAILED("The submitted data is not valid"),
    BAD_REQUEST("The request could not be read, or a required parameter is missing"),
    UNAUTHENTICATED("You are not signed in, or your session has been revoked"),
    NOT_FOUND("The requested item does not exist"),
    CONFLICT("The request conflicts with the current state of the data"),
    INTERNAL_ERROR("Something went wrong on the server");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String code() {
        return name();
    }

    public String message() {
        return message;
    }
}
