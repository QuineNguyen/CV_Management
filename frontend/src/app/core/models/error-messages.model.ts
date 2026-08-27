import { ClientErrorCode } from './error-code.model';
import { environment } from '../../../environments/environment';

/** Maps API error codes to user-friendly messages. */
export const ERROR_MESSAGES: Readonly<Record<ClientErrorCode, string>> = {
  // Identity and organisation
  DUPLICATE_EMAIL: 'That email address is already registered',
  DUPLICATE_USERNAME: 'That username is already taken',
  TEAM_MEMBERSHIP_REQUIRED:
    'Every employee needs at least one team, with exactly one marked as their primary team',
  LAST_ACTIVE_ADMIN:
    'This is the last active administrator. Promote someone else before making this change',
  TECH_LEAD_STILL_ASSIGNED:
    'This person still leads a team. Assign a replacement lead for each team first',

  // CV profiles and CVs
  PROFILE_NAME_TAKEN: 'You already have an active profile with that name',
  PRIMARY_PROFILE_EXISTS: 'This employee already has a primary profile',
  CV_LANGUAGE_EXISTS: 'This profile already has an active CV in that language',
  MASTER_CV_EXISTS: 'This profile already has a master CV',
  OPEN_DRAFT_EXISTS: 'This CV already has an open draft. Finish or cancel it first',
  VERSION_NUMBER_TAKEN: 'That version number is already taken. Reload and try again',
  NOT_CV_OWNER: 'Only the owner of a CV can edit its content',
  DRAFT_CONTENT_LOCKED:
    'This draft is read-only while it is awaiting approval, so reviewers decide on the exact ' +
    'content they read',

  // Approval
  APPROVAL_ALREADY_ASSIGNED: 'This draft already has an open approval assignment',

  // Update requests
  PENDING_REQUEST_EXISTS:
    'A pending request already exists for this employee, profile and language',
  CV_PROFILE_MISMATCH: 'The selected CV does not belong to the profile this request targets',

  // Catalogue
  DUPLICATE_SKILL_CODE: 'Another skill already uses that code',
  DUPLICATE_SKILL_NAME: 'Another skill already uses that name',

  // Reminders
  REMINDER_ALREADY_SENT: 'A reminder for this was already sent today',

  // Generic
  OUT_OF_SCOPE: 'You do not have access to this data',
  STALE_STATE: 'Someone changed this item first. Reload to see the current state',
  VALIDATION_FAILED: 'Please check the highlighted fields',
  BAD_REQUEST: 'The request could not be processed',
  UNAUTHENTICATED: 'Your session has ended. Please sign in again',
  NOT_FOUND: 'That item no longer exists',
  CONFLICT: 'This change conflicts with existing data',
  INTERNAL_ERROR: 'Something went wrong on the server. Please try again',
  NETWORK_ERROR: 'Cannot reach the server. Check your connection and try again',

  // Authentication
  INVALID_CREDENTIALS: 'Invalid username or password',
  ACCOUNT_LOCKED: 'Account is temporarily locked due to too many failed attempts. Please try again after 5 minutes',
  ACCOUNT_INACTIVE: 'Account is inactive',
  GOOGLE_TOKEN_INVALID: 'The Google session is invalid or expired',
  GOOGLE_EMAIL_NOT_REGISTERED: 'Your Google account is not registered. Please contact your administrator',
  GOOGLE_ACCOUNT_MISMATCH: 'The Google account does not match the registered account',
  INVALID_CURRENT_PASSWORD: 'Incorrect current password',
  PASSWORD_TOO_WEAK: 'Password must be at least 8 characters, including one uppercase letter, one lowercase letter, one number and one special character',
  PASSWORD_CONFIRMATION_MISMATCH: 'Passwords do not match',
  PASSWORD_SAME_AS_OLD: 'New password must be different from the old password',

  // Department
  DUPLICATE_DEPARTMENT_CODE: 'Department code already exists',
  DUPLICATE_DEPARTMENT_NAME: 'Department name already exists',
  DEPARTMENT_HAS_CHILDREN: 'Cannot move or delete department because it has children',
  DEPARTMENT_HAS_EMPLOYEES: 'Cannot move or delete department because it has employees',
  DEPARTMENT_CIRCULAR_REFERENCE: 'Cannot move department because it would create a circular reference',
  DEPARTMENT_INVALID_REORDER: 'The sorted list does not match the current data',

  // Team
  DUPLICATE_TEAM_CODE: 'This team code is already in use',
  INVALID_TECH_LEAD_ROLE: 'The selected user is not a Tech Lead',
  INVALID_TECH_LEAD_INACTIVE: 'The selected Tech Lead is not active',
  INACTIVE_USER_NOT_ASSIGNABLE: 'An inactive user cannot be added to a team',
  TEAM_HAS_MEMBERS: 'This team still has members. Remove them before deleting it',
  TEAM_HAS_PROFILES: 'This team is still linked to a CV profile',
  USER_ALREADY_IN_TEAM: 'This user already belongs to the team',
  CANNOT_REMOVE_ONLY_TEAM: 'A user must belong to at least one team',
  CANNOT_REMOVE_PRIMARY_TEAM: 'Change the primary team first, then remove this membership',

  // User
  PRIMARY_TEAM_REQUIRED: 'Select exactly one primary team',
  DUPLICATE_TEAM_ASSIGNMENT: 'The same team was assigned more than once',
  REPLACEMENT_TECH_LEAD_REQUIRED: 'Select a replacement Tech Lead for every team',
  INVALID_REPLACEMENT_TECH_LEAD: 'The replacement must be a different active Tech Lead',
  USER_ALREADY_ACTIVE: 'This account is already active',
  USER_ALREADY_INACTIVE: 'This account is already inactive',
  CANNOT_DEACTIVATE_SELF: 'You cannot deactivate your own account',
  CANNOT_CHANGE_OWN_ROLE: 'You cannot change your own role',
};

export function messageFor(code: string, fallback?: string): string {
  if (isKnownErrorCode(code)) {
    return ERROR_MESSAGES[code];
  }
  if (environment.warnOnUnknownErrorCode) {
    console.warn(
      `[errors] No wording for code "${code}". Add it to ERROR_MESSAGES and to ` +
        `ClientErrorCode in models/error-code.model.ts`,
    );
  }
  return fallback ?? ERROR_MESSAGES.INTERNAL_ERROR;
}

function isKnownErrorCode(code: string): code is ClientErrorCode {
  return code in ERROR_MESSAGES;
}