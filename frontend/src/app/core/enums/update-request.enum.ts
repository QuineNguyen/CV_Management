export enum UpdateRequestStatus {
    Pending = 'PENDING',
    Completed = 'COMPLETED',
    Cancelled = 'CANCELLED',
}

export enum UpdateRequestFilter {
    Status = 'status',
    Language = 'language',
    Department = 'department',
}

export const UPDATE_REQUEST_STATUS_LABELS: Record<UpdateRequestStatus, string> = {
    [UpdateRequestStatus.Pending]: 'Pending',
    [UpdateRequestStatus.Completed]: 'Completed',
    [UpdateRequestStatus.Cancelled]: 'Cancelled',
}