import { DiffChunk } from "../dtos/cv-version.dto";
import { ChangeType, DiffValueKind } from "../enums/cv-diff.enum";
import { CvSectionKey } from "../enums/cv-section-key.enum";

// A version ticked in the history list; kept by id so it survives paging
export interface VersionPick {
    id: string;
    versionNumber: number;
}

// The pair shown by the viewer; null "from" means an empty CV
export interface VersionPair {
    from: string | null;
    to: string;
}

export interface DiffFieldRow {
    key: string;
    label: string;
    kind: DiffValueKind;
    changeType: ChangeType;
    // Display form: option labels, dd/MM/yyyy dates
    oldText: string | null;
    newText: string | null;
    // Only for free text; codes, dates and numbers are shown whole
    inline: DiffChunk[] | null;
}

export interface DiffItemHeading {
    title: string;
    subtitle: string | null;
}

export interface DiffItemBlock {
    id: string;
    changeType: ChangeType;
    // Null on the side where the entry does not exist
    oldHeading: DiffItemHeading | null;
    newHeading: DiffItemHeading | null;
    fields: DiffFieldRow[];
}

export interface DiffSectionBlock {
    key: CvSectionKey;
    label: string;
    repeated: boolean;
    changeCount: number;
    items: DiffItemBlock[];
}