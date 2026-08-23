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

    // ---------- Authentication ----------
    INVALID_CREDENTIALS("The username or password is incorrect"),
    ACCOUNT_LOCKED("Your account is temporarily locked. Please try again later"),
    ACCOUNT_INACTIVE("This account has been deactivated"),
    INVALID_TOKEN("The authentication token is invalid or has expired"),
    GOOGLE_TOKEN_INVALID("The Google sign-in token could not be verified"),
    GOOGLE_EMAIL_NOT_REGISTERED("This Google email is not registered in the system. Please contact your administrator"),
    GOOGLE_ACCOUNT_MISMATCH("This Google account is already linked to a different user"),
    INVALID_CURRENT_PASSWORD("The current password is incorrect"),
    PASSWORD_TOO_WEAK("Password must be at least 8 characters with uppercase, lowercase, number and special character"),
    PASSWORD_CONFIRMATION_MISMATCH("The confirmation password does not match the new password"),
    PASSWORD_SAME_AS_OLD("The new password must be different from the current password"),
    MUST_CHANGE_PASSWORD("You must change your password before continuing"),

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

    // ---------- Department ----------
    DUPLICATE_DEPARTMENT_CODE("Another department already uses that code"),
    DUPLICATE_DEPARTMENT_NAME("Another department already uses that name"),
    DEPARTMENT_HAS_CHILDREN("Cannot delete a department that still has sub-departments"),
    DEPARTMENT_HAS_EMPLOYEES("Cannot delete a department that still has employees assigned to it"),
    DEPARTMENT_HAS_TEAMS("Cannot delete a department that still has teams"),
    DEPARTMENT_CIRCULAR_REFERENCE("Moving this department here would create a circular reference"),
    DEPARTMENT_INVALID_MOVE_TARGET("The anchor department does not belong to the target parent"),

    // ---------- Team ----------
    DUPLICATE_TEAM_CODE("Team code already exists"),
    INVALID_TECH_LEAD_ROLE("Selected user is not a tech lead"),
    INVALID_TECH_LEAD_INACTIVE("Selected tech lead is not active"),
    TEAM_HAS_MEMBERS("Team still has members"),
    TEAM_HAS_PROFILES("Team is still linked to CV profiles"),
    USER_ALREADY_IN_TEAM("User already belongs to this team"),
    CANNOT_REMOVE_ONLY_TEAM("User must belong to at least one team"),
    CANNOT_REMOVE_PRIMARY_TEAM("Cannot remove the primary team"),

    // ---------- User ----------
    PRIMARY_TEAM_REQUIRED("Exactly one primary team is required"),
    DUPLICATE_TEAM_ASSIGNMENT("Duplicated team assignment"),
    REPLACEMENT_TECH_LEAD_REQUIRED("Replacement tech lead is required"),
    INVALID_REPLACEMENT_TECH_LEAD("Invalid replacement tech lead"),
    USER_ALREADY_ACTIVE("User is already active"),
    USER_ALREADY_INACTIVE("User is already inactive"),

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
