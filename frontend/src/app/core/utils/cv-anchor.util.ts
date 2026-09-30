import { CvContent } from "../models/cv-content.model";
import { SectionDescriptor } from "../models/cv-section-descriptor.model";

/*
 * Which parts of a CV can take an anchored comment or note.
 * - A repeated section needs at least one saved entry: the anchor names its item_id.
 * - A single section needs at least one filled field.
 * Empty sections are left to the host's overall free-text field.
 */
const ITEM_ID_KEY = 'item_id';

type SectionData = Record<string, unknown>;

// Saved entries of a repeated section: only these carry an item_id
export function anchorableEntriesOf(content: CvContent, section: SectionDescriptor): SectionData[] {
    const raw = (content as unknown as Record<string, SectionData[] | undefined>)[section.key] ?? [];
    return raw.filter(item => !!item[ITEM_ID_KEY]);
}

export function itemIdOf(item: SectionData): string {
    return String(item[ITEM_ID_KEY]);
}

export function isSectionFilled(content: CvContent, section: SectionDescriptor): boolean {
    if (section.repeated) {
        return anchorableEntriesOf(content, section).length > 0;
    }
    // Declared fields only, so metadata keys never count as content
    const data = (content as unknown as Record<string, SectionData | undefined>)[section.key] ?? {};
    return section.fields.some(field => isFilled(data[field.key]));
}

// Stored shapes: trimmed text, number, ISO date or null; tags as a string list
function isFilled(value: unknown): boolean {
    if (value === null || value === undefined) {
        return false;
    }
    if (typeof value === 'string') {
        return value.trim().length > 0;
    }
    if (typeof value === 'number') {
        return Number.isFinite(value);
    }
    if (Array.isArray(value)) {
        return value.some(isFilled);
    }
    return true;
}