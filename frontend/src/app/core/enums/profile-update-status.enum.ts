export enum ProfileUpdateStatus {
    Pending = 'PENDING',
    Approved = 'APPROVED',
    Rejected = 'REJECTED',
    Cancelled = 'CANCELLED',
}

export const PROFILE_UPDATE_STATUS_LABELS: Record<ProfileUpdateStatus, string> = {
    [ProfileUpdateStatus.Pending]: 'Awaiting review',
    [ProfileUpdateStatus.Approved]: 'Approved',
    [ProfileUpdateStatus.Rejected]: 'Rejected',
    [ProfileUpdateStatus.Cancelled]: 'Withdrawn by user',
};

// Only PENDING can still be withdrawn or decided; the other three are final.
export const OPEN_PROFILE_UPDATE_STATUSES: readonly ProfileUpdateStatus[] = [
    ProfileUpdateStatus.Pending,
];