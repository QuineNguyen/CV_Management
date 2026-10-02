export enum BatchTargetType {
    Department = 'DEPARTMENT',
    Team = 'TEAM',
    Manual = 'MANUAL',
}

export enum BatchRequestStatus {
    Processing = 'PROCESSING',
    Completed = 'COMPLETED',
    CompletedWithErrors = 'COMPLETED_WITH_ERRORS',
}

export enum BatchExclusionReason {
    SelfRequest = 'SELF_REQUEST',
    AlreadyPending = 'ALREADY_PENDING',
    DuplicateInBatch = 'DUPLICATE_IN_BATCH',
}

// Codes the wizard reacts to itself, on top of the interceptor's toast
export enum BatchErrorCode {
    Empty = 'BATCH_EMPTY',
    PreviewOutdated = 'BATCH_PREVIEW_OUTDATED',
}

export enum BatchWizardStep {
    Criteria = 'CRITERIA',
    Preview = 'PREVIEW',
    Confirm = 'CONFIRM',
}

// Which dropdown of the criteria step is open
export enum BatchPicker {
    Department = 'DEPARTMENT',
    Team = 'TEAM',
    Employee = 'EMPLOYEE',
}

export enum BatchRouteParam {
    Id = 'id',
}

export const BATCH_TARGET_TYPE_ORDER: readonly BatchTargetType[] = [
    BatchTargetType.Department,
    BatchTargetType.Team,
    BatchTargetType.Manual,
];

export const BATCH_TARGET_TYPE_LABELS: Record<BatchTargetType, string> = {
    [BatchTargetType.Department]: 'By department',
    [BatchTargetType.Team]: 'By team',
    [BatchTargetType.Manual]: 'Pick employees',
};

export const BATCH_TARGET_TYPE_HINTS: Record<BatchTargetType, string> = {
    [BatchTargetType.Department]: 'The department and all its sub-departments',
    [BatchTargetType.Team]: 'Every member, the tech lead included',
    [BatchTargetType.Manual]: 'Tick employees one by one',
};

export const BATCH_TARGET_TYPE_ICONS: Record<BatchTargetType, string> = {
    [BatchTargetType.Department]: 'account_tree',
    [BatchTargetType.Team]: 'groups',
    [BatchTargetType.Manual]: 'checklist',
};

export const BATCH_STATUS_LABELS: Record<BatchRequestStatus, string> = {
    [BatchRequestStatus.Processing]: 'Processing',
    [BatchRequestStatus.Completed]: 'Completed',
    [BatchRequestStatus.CompletedWithErrors]: 'Completed with errors',
};

export const BATCH_EXCLUSION_LABELS: Record<BatchExclusionReason, string> = {
    [BatchExclusionReason.SelfRequest]: 'You cannot send a request to yourself',
    [BatchExclusionReason.AlreadyPending]: 'Already has a pending request',
    [BatchExclusionReason.DuplicateInBatch]: 'Duplicate in the list',
};