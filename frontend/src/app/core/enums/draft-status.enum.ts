// DRAFT / PENDING_* / REJECTED are open; PUBLISHED and CANCELLED are terminal.
export enum DraftStatus {
    Draft = 'DRAFT',
    PendingTechLead = 'PENDING_TECH_LEAD',
    PendingHr = 'PENDING_HR',
    Rejected = 'REJECTED',
    Published = 'PUBLISHED',
    Cancelled = 'CANCELLED',
}

export const DRAFT_STATUS_LABELS: Record<DraftStatus, string> = {
    [DraftStatus.Draft]: 'Draft',
    [DraftStatus.PendingTechLead]: 'Awaiting tech lead',
    [DraftStatus.PendingHr]: 'Awaiting HR',
    [DraftStatus.Rejected]: 'Rejected',
    [DraftStatus.Published]: 'Published',
    [DraftStatus.Cancelled]: 'Cancelled',
};

// Content is read-only for everyone, owner included, while a draft is under review.
export const LOCKED_DRAFT_STATUSES: readonly DraftStatus[] = [
    DraftStatus.PendingTechLead,
    DraftStatus.PendingHr,
];