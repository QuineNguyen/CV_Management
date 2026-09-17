export enum ApprovalLevel {
    Level1 = 'LEVEL_1',
    Level2 = 'LEVEL_2',
}

// What the reviewer at each level is actually responsible for
export const APPROVAL_LEVEL_LABELS: Record<ApprovalLevel, string> = {
    [ApprovalLevel.Level1]: 'Tech lead',
    [ApprovalLevel.Level2]: 'HR',
};

export const APPROVAL_LEVEL_DESCRIPTIONS: Record<ApprovalLevel, string> = {
    [ApprovalLevel.Level1]: 'Technical review of the content',
    [ApprovalLevel.Level2]: 'Format and spelling check',
};