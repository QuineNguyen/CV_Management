
/*
 * Turns the wire diff into what the viewer renders: template order, labels from the section
 * descriptors, option codes and dates in readable form.
 * - Dates are always shown in full: with the month/year style of the preview, a change of day
 * would show two identical values marked as modified.
 */

import { FieldDiff, ItemDiff, SectionDiff, VersionDiffResponse } from "../dtos/cv-version.dto";
import { ChangeType, CV_DIFF_EXTRA_FIELD_LABELS, CvDiffExtraField, DiffValueKind } from "../enums/cv-diff.enum";
import { CvLanguage } from "../enums/cv-language.enum";
import { DiffFieldRow, DiffItemBlock, DiffItemHeading, DiffSectionBlock } from "../models/cv-diff.model";
import { CV_SECTIONS, FieldDescriptor, FieldKind, optionsFor, SectionDescriptor } from "../models/cv-section-descriptor.model";

// Stored as a code, a date or a number: an inline diff of the raw value means nothing to a reader
const WHOLE_VALUE_KINDS: ReadonlySet<FieldKind> = new Set([FieldKind.Select, FieldKind.Date, FieldKind.Number]);

const EXTRA_FIELDS: ReadonlySet<string> = new Set<string>(Object.values(CvDiffExtraField));

// The server drops false flags, so "true" is what normally arrives
const FLAG_FIELDS: ReadonlySet<string> = new Set<string>([CvDiffExtraField.IsUntranslated, CvDiffExtraField.DeletedInMaster]);
const FLAG_LABELS: Readonly<Record<string, string>> = { true: 'Yes', false: 'No' };

// YYYY-MM-DD or YYYY-MM, as content_json stores dates
const ISO_DATE = /^(\d{4})-(\d{2})(?:-(\d{2}))?$/;

const FALLBACK_ITEM_NOUN = 'entry';

export function buildDiffSections(diff: VersionDiffResponse, language: CvLanguage): DiffSectionBlock[] {
    const byKey = new Map(diff.sections.map(section => [section.sectionKey, section]));
    return CV_SECTIONS
        .map(descriptor => toSectionBlock(descriptor, byKey.get(descriptor.key), language))
        // Empty on both sides: nothing to compare
        .filter(block => block.items.length > 0);
}

// "Changes only": unchanged sections, entries and fields drop out
export function keepChanges(sections: DiffSectionBlock[]): DiffSectionBlock[] {
    return sections
        .filter(section => section.changeCount > 0)
        .map(section => ({
            ...section,
            items: section.items
                .filter(item => item.changeType !== ChangeType.Unchanged)
                .map(item => ({
                    ...item,
                    fields: item.fields.filter(field => field.changeType !== ChangeType.Unchanged),
                })),
        }));
}

// ---------- Blocks ----------

function toSectionBlock(descriptor: SectionDescriptor, section: SectionDiff | undefined, language: CvLanguage): DiffSectionBlock {
    return {
        key: descriptor.key,
        label: descriptor.label,
        repeated: descriptor.repeated,
        changeCount: section?.changeCount ?? 0,
        items: (section?.items ?? []).map(item => toItemBlock(descriptor, item, language)),
    };
}

function toItemBlock(descriptor: SectionDescriptor, item: ItemDiff, language: CvLanguage): DiffItemBlock {
    const fields = item.fields
        .map(field => toFieldRow(descriptor, field, language))
        .sort((a, b) => fieldRank(descriptor, a.key) - fieldRank(descriptor, b.key));

    return {
        // SINGLE sections have no item id; the section key is unique within a diff
        id: item.itemId ?? descriptor.key,
        changeType: item.changeType,
        oldHeading: item.changeType === ChangeType.Added ? null : headingOf(descriptor, fields, row => row.oldText),
        newHeading: item.changeType === ChangeType.Removed ? null : headingOf(descriptor, fields, row => row.newText),
        fields,
    };
}

function toFieldRow(descriptor: SectionDescriptor, field: FieldDiff, language: CvLanguage): DiffFieldRow {
    const declared = descriptor.fields.find(candidate => candidate.key === field.fieldKey);
    return {
        key: field.fieldKey,
        label: labelOf(declared, field.fieldKey),
        kind: field.fieldKey === CvDiffExtraField.AvatarImageId ? DiffValueKind.Image : DiffValueKind.Text,
        changeType: field.changeType,
        oldText: display(declared, field.fieldKey, field.oldValue, language),
        newText: display(declared, field.fieldKey, field.newValue, language),
        inline: showsInline(declared) ? field.inlineDiff : null,
    };
}

// Title and subtitle of one side, from the fields the descriptor names
function headingOf(descriptor: SectionDescriptor, fields: DiffFieldRow[], pick: (row: DiffFieldRow) => string | null): DiffItemHeading {
    const read = (key: string | undefined): string | null => {
        const row = key ? fields.find(field => field.key === key) : undefined;
        return row ? pick(row) : null;
    };
    return {
        title: read(descriptor.titleField) ?? capitalise(descriptor.itemNoun ?? FALLBACK_ITEM_NOUN),
        subtitle: read(descriptor.subtitleField),
    }
}

// ---------- Values ----------

function labelOf(declared: FieldDescriptor | undefined, key: string): string {
    if (declared) {
        return declared.label;
    }
    return isExtraField(key) ? CV_DIFF_EXTRA_FIELD_LABELS[key] : humanise(key);
}

function display(declared: FieldDescriptor | undefined, key: string, value: string | null, language: CvLanguage): string | null {
    if (value === null) {
        return null;
    }
    if (FLAG_FIELDS.has(key)) {
        return FLAG_LABELS[value] ?? value;
    }
    if (declared?.kind === FieldKind.Select && declared.optionSet) {
        return optionsFor(declared.optionSet, language).find(option => option.value === value)?.label ?? value;
    }
    if (declared?.kind === FieldKind.Date) {
        return formatDate(value);
    }
    return value;
}

function showsInline(declared: FieldDescriptor | undefined): boolean {
    return !!declared && !WHOLE_VALUE_KINDS.has(declared.kind);
}

// Photo first, then template order, then anything the template does not declare
function fieldRank(descriptor: SectionDescriptor, key: string): number {
    if (key === CvDiffExtraField.AvatarImageId) {
        return -1;
    }
    const index = descriptor.fields.findIndex(field => field.key === key);
    return index >= 0 ? index : descriptor.fields.length;
}

// ---------- Helpers ----------

function isExtraField(key: string): key is CvDiffExtraField {
    return EXTRA_FIELDS.has(key);
}

function formatDate(value: string): string {
    const match = ISO_DATE.exec(value);
    if (!match) {
        return value;
    }
    const [, year, month, day] = match;
    return day ? `${day}/${month}/${year}` : `${month}/${year}`;
}

function humanise(key: string): string {
    return capitalise(key.replace(/_/g, ' '));
}

function capitalise(text: string): string {
    return text.charAt(0).toUpperCase() + text.slice(1);
}