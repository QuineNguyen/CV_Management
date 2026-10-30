import { CvLanguage } from "../enums/cv-language.enum";
import { DraftStatus } from "../enums/draft-status.enum";
import { LifecycleStatus } from "../enums/lifecycle-status.enum";
import { CvSearchSortField, CvSortField, SortDirection } from "../enums/sort-field.enum";
import { VersionSource } from "../enums/version-source.enum";
import { CvContent } from "../models/cv-content.model";
import { InlineCommentResponse } from "./inline-comment.dto";

export interface CvCreateRequest {
    language: CvLanguage;
    content: CvContent;
    avatarImageId: string | null;
}

export interface CvEditRequest {
    content: CvContent;
    avatarImageId: string | null;
    expectedDraftUpdatedAt: string | null;
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
    untranslatedItemCount: number;
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
}

// Content is null when the CV has no published version - the screen says so explicitly.
export interface CvDetailResponse {
    cv: CvResponse;
    currentVersion: CvVersionSummary | null;
    content: CvContent | null;
    avatarImageId: string | null;
    avatarUrl: string | null;
    openDraft: CvDraftResponse | null;
    lastCancellation: DraftCancellation | null;
}

// The two counts drive the structure-sync notice; updatedAt is the token sent back on save. 
export interface CvDraftResponse {
    id: string;
    cvId: string;
    status: DraftStatus;
    reviewRound: number;
    content: CvContent;
    avatarImageId: string | null;
    avatarUrl: string | null;
    lastRejectionReason: string | null;
    submittable: boolean;
    untranslatedItemCount: number;
    deletedInMasterCount: number;
    submittedAt: string | null;
    updatedAt: string;
    inlineComments: InlineCommentResponse[];
}

// One shape, two outcomes; directPublish says which branch the owner's role took.
export interface CvEditResponse {
    directPublish: boolean;
    draft: CvDraftResponse | null;
    publishedVersion: CvVersionSummary | null;
}

// Who cancelled the owner's last draft; reason is null when the draft was not under review.
export interface DraftCancellation {
    reason: string | null;
    cancelledByName: string | null;
    cancelledAt: string;
}

export interface CvSearchQuery {
    employeeName?: string;
    profileName?: string;
    departmentId?: string;
    teamId?: string;
    language?: CvLanguage;
    allProfiles?: boolean;
    keyword?: string;
    skillIds?: readonly string[];
    sortBy?: CvSearchSortField;
    direction?: SortDirection;
    page: number;
    size: number;
}

export interface CvSearchTeamTag {
    code: string;
    name: string;
    primary: boolean;
}

export interface CvSearchSkillTag {
    id: string;
    name: string;
}

// hasPublishedVersion = false is a CV that only matched organization criteria
// untranslatedItemCount reads the open draft first, the current version second
export interface CvSearchItemResponse {
    cvId: string;
    employeeId: string;
    employeeName: string;
    employeeEmail: string;
    avatarUrl: string | null;
    profileName: string;
    primaryProfile: boolean;
    departmentCode: string;
    departmentName: string;
    teams: CvSearchTeamTag[];
    language: CvLanguage;
    hasPublishedVersion: boolean;
    currentVersionNumber: number | null;
    publishedAt: string | null;
    skills: CvSearchSkillTag[];
    untranslatedItemCount: number;
}

// depth indents departments so the dropdown reads as the tree
export interface CvSearchFilterOption {
    id: string;
    code: string;
    name: string;
    depth: number;
}

export interface CvSearchFiltersResponse {
    departments: CvSearchFilterOption[];
    teams: CvSearchFilterOption[];
}

export interface CvPdfFile {
    blob: Blob;
    // From Content-Disposition; null when the header is missing or not exposed
    fileName: string | null;
}

export interface CvPdfLinkResponse {
    path: string;
    fileName: string;
    expiresInSeconds: number;
}