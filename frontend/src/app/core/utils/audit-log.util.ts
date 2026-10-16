import { AuditActionTone, AuditFieldChange } from "../enums/audit-log.enum";
import { AuditFieldDiff } from "../models/audit-log-page.model";

/*
 * First word of a server action name -> badge tone. Unknown verbs fall back to neutral, so a new
 * backend action still renders, just without a colour.
 */
const VERB_TONES: Readonly<Record<string, AuditActionTone>> = {
    CREATE: AuditActionTone.Create,
    ADD: AuditActionTone.Create,
    APPROVE: AuditActionTone.Create,
    ACTIVATE: AuditActionTone.Create,
    RESTORE: AuditActionTone.Create,
    PUBLISH: AuditActionTone.Create,
    SUBMIT: AuditActionTone.Create,
    UPDATE: AuditActionTone.Change,
    EDIT: AuditActionTone.Change,
    MOVE: AuditActionTone.Change,
    REASSIGN: AuditActionTone.Change,
    ROLLBACK: AuditActionTone.Change,
    RESET: AuditActionTone.Change,
    CHANGE: AuditActionTone.Change,
    SYNC: AuditActionTone.Change,
    DELETE: AuditActionTone.Remove,
    DEACTIVATE: AuditActionTone.Remove,
    CANCEL: AuditActionTone.Remove,
    REJECT: AuditActionTone.Remove,
    REMOVE: AuditActionTone.Remove,
};

const JSON_INDENT = 2;

export function actionToneOf(action: string): AuditActionTone {
    const verb = action.split('_')[0];
    return VERB_TONES[verb] ?? AuditActionTone.Neutral;
}

// Pretty JSON when the value parses, the raw text otherwise
export function prettyValue(raw: string | null): string {
    if (raw === null || raw.trim() === '') {
        return '';
    }
    const parsed = parseJson(raw);
    return parsed === undefined ? raw : JSON.stringify(parsed, null, JSON_INDENT);
}

/*
 * Top-level fields that differ between two JSON objects. Anything that is not an object on both
 * sides (plain text, arrays, a create with no old value) yields no list - the side-by-side view
 * already says everything.
 */
export function diffTopLevel(oldRaw: string | null, newRaw: string | null): AuditFieldDiff[] {
    const before = asRecord(oldRaw);
    const after = asRecord(newRaw);
    if (!before || !after) {
        return [];
    }

    const keys = new Set([...Object.keys(before), ...Object.keys(after)]);
    const diffs: AuditFieldDiff[] = [];
    for (const key of keys) {
        if (!(key in before)) {
            diffs.push({ key, change: AuditFieldChange.Added });
        } else if (!(key in after)) {
            diffs.push({ key, change: AuditFieldChange.Removed });
        } else if (JSON.stringify(before[key]) !== JSON.stringify(after[key])) {
            diffs.push({ key, change: AuditFieldChange.Modified });
        }
    }
    return diffs;
}

// Local calendar date as yyyy-MM-dd; toISOString would shift it to UTC
export function toIsoDate(date: Date): string {
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
}

function parseJson(raw: string): unknown {
    try {
        return JSON.parse(raw);
    } catch {
        return undefined;
    }
}

function asRecord(raw: string | null): Record<string, unknown> | null {
    if (!raw) {
        return null;
    }
    const parsed = parseJson(raw);
    return parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed as Record<string, unknown>
        : null;
}