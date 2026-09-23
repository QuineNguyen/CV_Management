import { ApprovalLevel } from "../enums/approval-level.enum";
import { AssignmentStatus } from "../enums/assignment-status.enum";
import { CvLanguage } from "../enums/cv-language.enum";
import { DecisionResult } from "../enums/decision-result.enum";
import { DraftStatus } from "../enums/draft-status.enum";
import { ApprovalSortField, PendingDraftSortField, SortDirection } from "../enums/sort-field.enum";
import { UserRole } from "../enums/user-role.enum";
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

export interface CancelledReviewItem {
    assignmentId: string;
    cvLanguage: CvLanguage | null;
    profileName: string | null;
    employeeName: string | null;
    level: ApprovalLevel;
    reviewRound: number;
    assignedAt: string;
    cancelledAt: string | null;
    cancelledByName: string | null;
    reason: string | null;
}

export interface CancelledReviewQuery {
    page: number;
    size: number;
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

export interface DraftApproveResponse {
    draftId: string;
    newStatus: DraftStatus;
    versionId: string | null;
}

export interface CancelDraftRequest {
    // Omitted by the owner, required by an admin on a draft under review.
    reason?: string;
}

export interface DraftCancelResponse {
    draftId: string;
    newStatus: DraftStatus;
    assignmentsCancelled: number;
    commentsResolved: number;
}

export interface ReassignRequest {
    newAssigneeId: string;
    reason: string;
}

export interface ReassignResponse {
    draftId: string;
    newAssigneeId: string;
    newAssigneeName: string;
    newDueAt: string;
}

export interface ReassignCandidate {
    userId: string;
    fullName: string;
    username: string;
    role: UserRole;
    openAssignmentCount: number;
}

export interface PendingDraftItem {
    draftId: string;
    cvId: string;
    language: CvLanguage | null;
    profileName: string | null;
    employeeName: string | null;
    status: DraftStatus;
    level: ApprovalLevel | null;
    reviewRound: number;
    assigneeName: string | null;
    submittedAt: string | null;
    dueAt: string | null;
    slaRemainingMinutes: number;
}

export interface PendingDraftQuery {
    page: number;
    size: number;
    sortBy?: PendingDraftSortField;
    direction?: SortDirection;
}