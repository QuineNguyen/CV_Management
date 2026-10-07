import { ChangeType, DiffChunkType } from "../enums/cv-diff.enum";
import { CvSectionKey } from "../enums/cv-section-key.enum";
import { CvVersionSortField, SortDirection } from "../enums/sort-field.enum";
import { VersionSource } from "../enums/version-source.enum";

export interface CvVersionHistoryItem {
    id: string;
    versionNumber: number;
    publishedAt: string;
    source: VersionSource;
    authoredByName: string | null;
    level1ApproverName: string | null;
    level2ApproverName: string | null;
    // Set only for ROLLBACK; author and reviewers are then inherited from that version
    rollbackSourceVersionNumber: number | null;
    // v(n-1); null for v1
    previousVersionId: string | null;
}

export interface CvVersionQuery {
    page: number;
    size: number;
    sortBy?: CvVersionSortField;
    direction?: SortDirection;
}

export interface CvRollbackRequest {
    targetVersionId: string;
}

// EQUAL shows on both sides, DELETE only on the old side, INSERT only on the new side
export interface DiffChunk {
    type: DiffChunkType;
    text: string;
}

export interface FieldDiff {
    fieldKey: string;
    changeType: ChangeType;
    oldValue: string | null;
    newValue: string | null;
    // Only for MODIFIED with a value on both sides
    inlineDiff: DiffChunk[] | null;
}

export interface ItemDiff {
    // Null for SINGLE sections
    itemId: string | null;
    changeType: ChangeType;
    fields: FieldDiff[];
}

export interface SectionDiff {
    sectionKey: CvSectionKey;
    changeCount: number;
    items: ItemDiff[];
}

export interface DiffStats {
    added: number;
    modified: number;
    removed: number;
}

export interface VersionDiffSide {
    versionNumber: number;
    publishedAt: string;
    source: VersionSource;
    avatarUrl: string | null;
}

// GET /cvs/{id}/versions/diff; fromVersion is null when compared with an empty CV
export interface VersionDiffResponse {
    fromVersion: VersionDiffSide | null;
    toVersion: VersionDiffSide;
    stats: DiffStats;
    sections: SectionDiff[];
}