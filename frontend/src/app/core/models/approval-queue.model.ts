import { CvSectionKey } from "../enums/cv-section-key.enum";
import { CvContent } from "./cv-content.model";

export interface ApprovalQueuePageState {
    index: number;
    size: number;
    total: number;
}

/*
 * How urgent an assignment looks. Derived from the minutes the server computed, never from the
 * browser clock - two reviewers in different time zones must read the same badge.
 */
export enum SlaTone {
    Comfortable = 'is-comfortable',
    DueSoon = 'is-due-soon',
    Overdue = 'is-overdue',
}

const MINUTES_PER_DAY = 60 * 24;

// Under a day left turns amber; past the deadline turns red.
export function slaToneOf(remainingMinutes: number): SlaTone {
    if (remainingMinutes < 0) {
        return SlaTone.Overdue;
    }
    return remainingMinutes < MINUTES_PER_DAY ? SlaTone.DueSoon : SlaTone.Comfortable;
}

// Short, readable countdown: "2d 4h left", "3h left", "Overdue by 5h".
export function slaLabelOf(remainingMinutes: number): string {
    const overdue = remainingMinutes < 0;
    const total = Math.abs(remainingMinutes);
    const days = Math.floor(total / MINUTES_PER_DAY);
    const hours = Math.floor((total % MINUTES_PER_DAY) / 60);

    const amount = days > 0 ? `${days}d ${hours}h` : `${hours}h`;
    return overdue ? `Overdue by ${amount}` : `${amount} left`;
}

/*
 * The sections a draft must fill before it can be submitted.
 *
 * The server validates the same three and is the authority; this runs client-side only so the
 * toast can name exactly what is missing. Re-deriving it here costs nothing because the screen
 * already holds the draft content.
 */
export const REQUIRED_SUBMIT_SECTIONS: readonly CvSectionKey[] = [
    CvSectionKey.PersonalInfo,
    CvSectionKey.Skills,
    CvSectionKey.Experience,
];

export function missingRequiredSections(content: CvContent | null | undefined): CvSectionKey[] {
    if (!content) {
        return [...REQUIRED_SUBMIT_SECTIONS];
    }

    const missing: CvSectionKey[] = [];

    if (!content.personal_info?.full_name?.trim()) {
        missing.push(CvSectionKey.PersonalInfo);
    }
    if (!content.skills?.length) {
        missing.push(CvSectionKey.Skills);
    }
    if (!content.experience?.length) {
        missing.push(CvSectionKey.Experience);
    }
    return missing;
}