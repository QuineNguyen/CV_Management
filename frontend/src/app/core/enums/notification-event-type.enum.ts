export enum NotificationEventType {
    DraftSubmitted = 'DRAFT_SUBMITTED',
    DraftApprovedLevel1 = 'DRAFT_APPROVED_LEVEL_1',
    DraftApprovedLevel2 = 'DRAFT_APPROVED_LEVEL_2',
    DraftRejected = 'DRAFT_REJECTED',
    DraftCancelled = 'DRAFT_CANCELLED',
    AssignmentReassigned = 'ASSIGNMENT_REASSIGNED',
    CvDeleted = 'CV_DELETED',
    CvRestored = 'CV_RESTORED',
    CvProfileDeleted = 'CV_PROFILE_DELETED',
    CvUpdateRequested = 'CV_UPDATE_REQUESTED',
    CvUpdateRequestCancelled = 'CV_UPDATE_REQUEST_CANCELLED',
    ProfileUpdateSubmitted = 'PROFILE_UPDATE_SUBMITTED',
    ProfileUpdateDecided = 'PROFILE_UPDATE_DECIDED',
    PasswordReset = 'PASSWORD_RESET',
    AccountCreated = 'ACCOUNT_CREATED',
    TeamHandover = 'TEAM_HANDOVER',
}

export const NOTIFICATION_TYPE_LABELS: Record<NotificationEventType, string> = {
    [NotificationEventType.DraftSubmitted]: 'Review request',
    [NotificationEventType.DraftApprovedLevel1]: 'Review request',
    [NotificationEventType.DraftApprovedLevel2]: 'CV published',
    [NotificationEventType.DraftRejected]: 'Changes requested',
    [NotificationEventType.DraftCancelled]: 'Review cancelled',
    [NotificationEventType.AssignmentReassigned]: 'Review reassigned',
    [NotificationEventType.CvDeleted]: 'CV deleted',
    [NotificationEventType.CvRestored]: 'CV restored',
    [NotificationEventType.CvProfileDeleted]: 'Profile deleted',
    [NotificationEventType.CvUpdateRequested]: 'CV update request',
    [NotificationEventType.CvUpdateRequestCancelled]: 'Request cancelled',
    [NotificationEventType.ProfileUpdateSubmitted]: 'Profile update request',
    [NotificationEventType.ProfileUpdateDecided]: 'Profile update result',
    [NotificationEventType.PasswordReset]: 'Security',
    [NotificationEventType.AccountCreated]: 'Account created',
    [NotificationEventType.TeamHandover]: 'Team handover',
};

// Material Symbols name per type
export const NOTIFICATION_TYPE_ICONS: Record<NotificationEventType, string> = {
    [NotificationEventType.DraftSubmitted]: 'rate_review',
    [NotificationEventType.DraftApprovedLevel1]: 'rate_review',
    [NotificationEventType.DraftApprovedLevel2]: 'verified',
    [NotificationEventType.DraftRejected]: 'assignment_return',
    [NotificationEventType.DraftCancelled]: 'cancel',
    [NotificationEventType.AssignmentReassigned]: 'swap_horiz',
    [NotificationEventType.CvDeleted]: 'delete',
    [NotificationEventType.CvRestored]: 'restore_from_trash',
    [NotificationEventType.CvProfileDeleted]: 'folder_delete',
    [NotificationEventType.CvUpdateRequested]: 'edit_note',
    [NotificationEventType.CvUpdateRequestCancelled]: 'edit_off',
    [NotificationEventType.ProfileUpdateSubmitted]: 'manage_accounts',
    [NotificationEventType.ProfileUpdateDecided]: 'how_to_reg',
    [NotificationEventType.PasswordReset]: 'lock_reset',
    [NotificationEventType.AccountCreated]: 'person_add',
    [NotificationEventType.TeamHandover]: 'groups',
};

export enum NotificationFilter {
    All = 'ALL',
    Unread = 'UNREAD',
}