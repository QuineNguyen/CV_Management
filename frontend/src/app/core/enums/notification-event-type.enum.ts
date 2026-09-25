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
    ProfileUpdateSubmitted = 'PROFILE_UPDATE_SUBMITTED',
    ProfileUpdateDecided = 'PROFILE_UPDATE_DECIDED',
    PasswordReset = 'PASSWORD_RESET',
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
    [NotificationEventType.ProfileUpdateSubmitted]: 'Profile update request',
    [NotificationEventType.ProfileUpdateDecided]: 'Profile update result',
    [NotificationEventType.PasswordReset]: 'Security',
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
    [NotificationEventType.ProfileUpdateSubmitted]: 'manage_accounts',
    [NotificationEventType.ProfileUpdateDecided]: 'how_to_reg',
    [NotificationEventType.PasswordReset]: 'lock_reset',
};

export enum NotificationFilter {
    All = 'ALL',
    Unread = 'UNREAD',
}