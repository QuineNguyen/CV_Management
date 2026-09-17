import { ApprovalLevel } from "../enums/approval-level.enum";
import { AssignmentStatus } from "../enums/assignment-status.enum";
import { CvLanguage } from "../enums/cv-language.enum";
import { DecisionResult } from "../enums/decision-result.enum";
import { DraftStatus } from "../enums/draft-status.enum";
import { ApprovalSortField, SortDirection } from "../enums/sort-field.enum";
import { CvDraftResponse } from "./cv.dto";

export interface ApprovalQueueItem {
    assignmentId: string;
    draftId: string;
    cvLanguage: CvLanguage | null;
    profileName: string | null;
    employeeName: string | null;
    level: ApprovalLevel;
    reviewRound: number;
    assignedAt: string;
    dueAt: string;
    // Computed server-side. Negative once the deadline has passed.
    slaRemainingMinutes: number;
}

export interface ApprovalQueueQuery {
    page: number;
    size: number;
    sortBy?: ApprovalSortField;
    direction?: SortDirection;
}

// Result of submitting a draft.
export interface DraftSubmitResponse {
    draftId: string;
    newStatus: DraftStatus;
    reviewRound: number;
    // True when the submitter was the only eligible tech lead, so the draft went straight to HR.
    level1Skipped: boolean;
}

export interface ApprovalAssignmentResponse {
    id: string;
    level: ApprovalLevel;
    reviewRound: number;
    reason: string;
    assignedAt: string;
    dueAt: string;
    slaRemainingMinutes: number;
}

export interface ApprovalDecisionResponse {
    id: string;
    level: ApprovalLevel;
    reviewRound: number;
    approverName: string | null;
    result: DecisionResult;
    reason: string | null;
    decidedAt: string;
}

// Everything the review screen renders, in one round trip.
export interface DraftReviewResponse {
    draft: CvDraftResponse;
    cvLanguage: CvLanguage;
    profileName: string | null;
    employeeName: string | null;
    avatarUrl: string | null;
    currentAssignment: ApprovalAssignmentResponse;
    previousDecisions: ApprovalDecisionResponse[];
}