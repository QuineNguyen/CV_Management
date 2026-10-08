import { CvLanguage, UpdateRequestLanguage } from "../enums/cv-language.enum";
import { CvSectionKey } from "../enums/cv-section-key.enum";
import { SortDirection, UpdateRequestSortField } from "../enums/sort-field.enum";
import { UpdateRequestStatus } from "../enums/update-request.enum";

// One feedback note pinned to (section, entry, field); the entry is required in repeated sections
export interface AnchoredNoteRequest {
    sectionKey: CvSectionKey;
    itemId: string | null;
    fieldKey: string | null;
    note: string;
}

export interface CreateSingleUpdateRequest {
    employeeId: string;
    // null: the employee creates a new profile for this CV
    profileId: string | null;
    language: UpdateRequestLanguage;
    reason: string;
    // yyyy-MM-dd; the server stores 23:59:59 of that day
    deadline: string;
    anchoredNotes: AnchoredNoteRequest[] | null;
}

export interface UpdateRequestQuery {
    status?: UpdateRequestStatus;
    departmentId?: string;
    language?: CvLanguage;
    fromDate?: string;
    toDate?: string;
    batchId?: string;
    sortBy?: UpdateRequestSortField;
    direction?: SortDirection;
    page: number;
    size: number;
}

export interface AnchoredNoteResponse {
    sectionKey: CvSectionKey;
    itemId: string | null;
    fieldKey: string | null;
    note: string;
}

export interface UpdateRequestResponse {
    id: string;
    employeeId: string;
    employeeName: string | null;
    profileId: string | null;
    profileName: string | null;
    cvId: string | null;
    language: CvLanguage;
    reason: string;
    deadline: string;
    status: UpdateRequestStatus;
    anchoredNotes: AnchoredNoteResponse[];
    createdByName: string | null;
    createdAt: string;
    cancellable: boolean;
    batchRequestId: string | null;
}

// A language left out of an ALL request because a request was already pending there
export interface SkippedLanguageResponse {
    language: CvLanguage;
    reason: string;
}

export interface CreateUpdateRequestResponse {
    created: UpdateRequestResponse[];
    skipped: SkippedLanguageResponse[];
}

// One row per anchored note of the pending update request
export interface PendingNoteResponse {
    sectionKey: CvSectionKey;
    itemId: string | null;
    fieldKey: string | null;
    note: string;
    createdByName: string | null;
    createdAt: string;
}