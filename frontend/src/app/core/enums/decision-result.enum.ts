// Outcome of one recorded approval decision.
export enum DecisionResult {
    Approved = 'APPROVED',
    Rejected = 'REJECTED',
    Skipped = 'SKIPPED',
}

export const DECISION_RESULT_LABELS: Record<DecisionResult, string> = {
    [DecisionResult.Approved]: 'Approved',
    [DecisionResult.Rejected]: 'Rejected',
    [DecisionResult.Skipped]: 'Skipped',
};

// Badge tone per outcome, so the history list reads at a glance.
export const DECISION_RESULT_TONES: Record<DecisionResult, string> = {
    [DecisionResult.Approved]: 'is-approved',
    [DecisionResult.Rejected]: 'is-rejected',
    [DecisionResult.Skipped]: 'is-skipped',
};