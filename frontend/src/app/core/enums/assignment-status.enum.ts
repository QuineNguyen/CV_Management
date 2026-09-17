// Lifecycle of one approval assignment.
export enum AssignmentStatus {
    Assigned = 'ASSIGNED',
    Completed = 'COMPLETED',
    Reassigned = 'REASSIGNED',
    Skipped = 'SKIPPED',
    Cancelled = 'CANCELLED',
}

export const ASSIGNMENT_STATUS_LABELS: Record<AssignmentStatus, string> = {
    [AssignmentStatus.Assigned]: 'Waiting for review',
    [AssignmentStatus.Completed]: 'Reviewed',
    [AssignmentStatus.Reassigned]: 'Handed over',
    [AssignmentStatus.Skipped]: 'Skipped',
    [AssignmentStatus.Cancelled]: 'Cancelled',
};