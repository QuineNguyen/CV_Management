import { CvLanguage } from "../enums/cv-language.enum";
import { DraftStatus } from "../enums/draft-status.enum";
import { LifecycleStatus } from "../enums/lifecycle-status.enum";
import { CvSortField, SortDirection } from "../enums/sort-field.enum";
import { VersionSource } from "../enums/version-source.enum";
import { CvContent } from "../models/cv-content.model";

export interface CvCreateRequest {
    language: CvLanguage;
    content?: CvContent | null;
    avatarImageId?: string | null;
}

export interface CvEditRequest {
    content: CvContent;
    avatarImageId?: string | null;
}

export interface CvDeleteRequest {
    newMasterCvId?: string | null;
}

export interface DeletedCvQuery {
    page: number;
    size: number;
    sortBy?: CvSortField;
    direction?: SortDirection;
}

export interface CvResponse {
    id: string;
    profileId: string;
    profileName: string | null;
    employeeId: string | null;
    employeeName: string | null;
    language: CvLanguage;
    master: boolean;
    masterCvId: string | null;
    lifecycleStatus: LifecycleStatus;
    currentVersionNumber: number | null;
    currentVersionPublishedAt: string | null;
    openDraftStatus: DraftStatus | null;
    deletedBy: string | null;
    deletedByName: string | null;
    deletedAt: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface CvVersionSummary {
    id: string;
    versionNumber: number;
    publishedAt: string;
    source: VersionSource;
    authoredBy: string;
    level1ApproverId: string | null;
    level2ApproverId: string | null;
    changeSummary: string | null;
}

// Content is null when the CV has no published version - the screen says so explicitly.
export interface CvDetailResponse {
    cv: CvResponse;
    currentVersion: CvVersionSummary | null;
    content: CvContent | null;
    avatarImageId: string | null;
    openDraft: CvDraftResponse | null;
}

export interface CvDraftResponse {
    id: string;
    cvId: string;
    status: DraftStatus;
    reviewRound: number;
    content: CvContent;
    avatarImageId: string | null;
    lastRejectionReason: string | null;
    submittable: boolean;
    untranslatedItemCount: number;
    submittedAt: string | null;
    updatedAt: string;
}

// One shape, two outcomes; directPublish says which branch the owner's role took.
export interface CvEditResponse {
    directPublish: boolean;
    draft: CvDraftResponse | null;
    publishedVersion: CvVersionSummary | null;
}