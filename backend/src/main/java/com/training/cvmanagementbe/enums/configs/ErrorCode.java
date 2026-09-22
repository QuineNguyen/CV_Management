package com.training.cvmanagementbe.enums.configs;

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
    CANNOT_DELETE_PRIMARY_PROFILE("Cannot delete the primary profile; assign another profile as primary first"),
    PROFILE_HAS_PENDING_DRAFTS("Cannot delete a profile that has CVs awaiting approval"),
    LINKED_TEAM_NOT_MEMBER("The linked team must be one the employee belongs to"),
    DUPLICATE_CV_ITEM_ID("Two entries in this CV share the same item identifier"),
    ITEM_ID_FOREIGN_TO_PROFILE("An entry identifier in this content belongs to a different profile"),

    // ---------- Approval ----------
    APPROVAL_ALREADY_ASSIGNED("This draft already has an open approval assignment"),
    DRAFT_MISSING_REQUIRED_SECTIONS("Personal info, at least one skill and at least one experience entry are required before submitting"),
    APPROVER_NOT_AVAILABLE("No eligible approver could be resolved for this CV"),
    NOT_CURRENT_ASSIGNEE("This CV has already been handled or reassigned to someone else"),
    DRAFT_NOT_REJECTED("Only a rejected draft can be resubmitted"),
    INVALID_COMMENT_ANCHOR("A comment points at a section, entry or field that does not exist in this draft"),
    CANNOT_REPLY_RESOLVED("This comment belongs to a finished review round and can no longer be replied to"),
    CANNOT_REPLY_COMMENT("Only the CV owner or the current reviewer can reply to this comment"),
    DRAFT_CANCEL_FORBIDDEN("Only the CV owner or an administrator can cancel this draft"),
    DRAFT_NOT_CANCELLABLE("This draft can no longer be cancelled from its current status"),
    DRAFT_CANCEL_REASON_REQUIRED("A reason is required when an administrator cancels a draft under review"),
    REASSIGN_SAME_PERSON("The new reviewer is the one already assigned"),
    REASSIGN_INVALID_CANDIDATE("The selected person is not eligible to review this draft at this level"),
    REASSIGN_NO_OPEN_ASSIGNMENT("This draft has no open assignment to transfer"),

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
    DEPARTMENT_CIRCULAR_REFERENCE("Moving this department here would create a circular reference"),
    DEPARTMENT_INVALID_MOVE_TARGET("The anchor department does not belong to the target parent"),

    // ---------- Team ----------
    DUPLICATE_TEAM_CODE("Team code already exists"),
    INVALID_TECH_LEAD_ROLE("Selected user is not a tech lead"),
    INVALID_TECH_LEAD_INACTIVE("Selected tech lead is not active"),
    INACTIVE_USER_NOT_ASSIGNABLE("Inactive user cannot be assigned to a team"),
    TEAM_HAS_MEMBERS("Team still has members"),
    TEAM_HAS_PROFILES("Team is still linked to CV profiles"),
    USER_ALREADY_IN_TEAM("User already belongs to this team"),
    CANNOT_REMOVE_ONLY_TEAM("User must belong to at least one team"),
    CANNOT_REMOVE_PRIMARY_TEAM("Cannot remove the primary team"),
    CANNOT_REMOVE_LEADING_MEMBER("Cannot remove the tech lead from the team they manage; reassign the lead first"),
    CANNOT_DELETE_PRIMARY_TEAM("Cannot delete a team that is someone's primary team"),
    CANNOT_DELETE_ONLY_TEAM("Cannot delete a team that is the only team of a member"),

    // ---------- User ----------
    PRIMARY_TEAM_REQUIRED("Exactly one primary team is required"),
    DUPLICATE_TEAM_ASSIGNMENT("Duplicated team assignment"),
    REPLACEMENT_TECH_LEAD_REQUIRED("Replacement tech lead is required"),
    INVALID_REPLACEMENT_TECH_LEAD("Invalid replacement tech lead"),
    USER_ALREADY_ACTIVE("User is already active"),
    USER_ALREADY_INACTIVE("User is already inactive"),
    CANNOT_DEACTIVATE_SELF("You cannot deactivate your own account"),
    CANNOT_CHANGE_OWN_ROLE("You cannot change your own role"),
    HR_CANNOT_MANAGE_ROLE("HR can only manage employee and tech lead accounts"),

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
    INTERNAL_ERROR("Something went wrong on the server"),

    // ---------- CV delete & restore ----------
    CV_SLOT_OCCUPIED("An active CV already exists for this profile and language"),
    CV_PROFILE_DELETED("The parent profile has been deleted; restore it first"),
    CV_NOT_DELETED("This CV is not in DELETED status"),
    CV_MASTER_CONFLICT("Restoring this CV would create two masters in the same profile"),
    MUST_DESIGNATE_NEW_MASTER("This CV is the master; designate a replacement before deleting"),
    INVALID_NEW_MASTER("The designated master must be another active CV of the same profile"),
    CV_HAS_PENDING_DRAFTS("Cannot delete a CV that has drafts awaiting approval"),

    // ---------- CV Profile restore ----------
    PROFILE_NAME_CONFLICT_ON_RESTORE("Another active profile of this employee already uses that name; provide a new name"),
    PROFILE_TEAM_INVALID_ON_RESTORE("The linked team is no longer one the employee belongs to; select a current team"),
    PROFILE_NOT_DELETED("This profile is not in DELETED status"),

    // ---------- Image upload ----------
    IMAGE_EMPTY("The uploaded file is empty"),
    IMAGE_TOO_LARGE("Image must be 5 MB or smaller"),
    IMAGE_UNSUPPORTED_TYPE("Only JPEG and PNG images are accepted"),
    IMAGE_STORAGE_UNAVAILABLE("The image store is not reachable right now; please try again"),

    // ---------- Profile update requests ----------
    PROFILE_UPDATE_PENDING_EXISTS("You already have a pending profile update request"),
    PROFILE_UPDATE_NOT_PENDING("This request has already been processed"),
    PROFILE_UPDATE_NO_CHANGES("The request does not change any of your current values"),
    PROFILE_UPDATE_NO_PENDING_REQUEST("You have no pending profile update request to cancel");

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
