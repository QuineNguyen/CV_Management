import { BatchExclusionReason, BatchRequestStatus, BatchTargetType } from "../enums/batch-request.enum";
import { CvLanguage } from "../enums/cv-language.enum";
import { PagedResponse } from "./page.dto";

export interface BatchPreviewRequest {
    targetType: BatchTargetType;
    targetIds: string[];
    language: CvLanguage;
    // yyyy-MM-dd; the servers adds 23:59:59
    deadline: string;
    reason: string;
}

export interface CreateBatchRequest extends BatchPreviewRequest {
    // Recipient count the user confirmed on the preview
    expectedCount: number;
}

export interface BatchPreviewQuery {
    includedPage: number;
    excludedPage: number;
    size: number;
}

export interface BatchPageQuery {
    page: number;
    size: number;
}

export interface BatchListQuery {
    status?: BatchRequestStatus;
    page: number;
    size: number;
}

export interface PreviewEmployee {
    fullName: string;
    departmentName: string | null;
    profileName: string | null;
}

export interface ExcludedEmployee {
    fullName: string;
    reason: BatchExclusionReason;
}

export interface BatchPreviewResponse {
    included: PagedResponse<PreviewEmployee>;
    excluded: PagedResponse<ExcludedEmployee>;
}

export interface BatchRequestResponse {
    id: string;
    targetType: BatchTargetType;
    targetLabel: string;
    language: CvLanguage;
    reason: string;
    deadline: string;
    totalCount: number;
    processedCount: number;
    errorCount: number;
    status: BatchRequestStatus;
    createdAt: string;
    createdByName: string | null;
}

export interface BatchFailedItemResponse {
    updateRequestId: string;
    fullName: string | null;
    email: string | null;
    profileName: string | null;
}