import { Injectable, signal } from "@angular/core";
import { PendingNoteResponse } from "../dtos/update-request.dto";

/*
 * Notes of the pending update request, provided by the content editor so section and item
 * editors read them by anchor. Kept apart from InlineCommentStore on purpose: these notes are
 * one-way (no replies, no review rounds) and must never mix into a review thread.
 */
@Injectable()
export class PendingNoteStore {

    readonly notes = signal<PendingNoteResponse[]>([]);

    // Notes on the anchor itself (a section or one entry), not on one of its fields
    anchoredTo(sectionKey: string, itemId: string | null): PendingNoteResponse[] {
        return this.notes().filter(note =>
            note.sectionKey === sectionKey && (note.itemId ?? null) === itemId && !note.fieldKey
        );
    }

    // Field notes of one anchor, keyed by field key
    byField(sectionKey: string, itemId: string | null): Map<string, PendingNoteResponse[]> {
        const result = new Map<string, PendingNoteResponse[]>();
        for (const note of this.notes()) {
            if (note.sectionKey !== sectionKey || (note.itemId ?? null) !== itemId || !note.fieldKey) {
                continue;
            }
            result.set(note.fieldKey, [...(result.get(note.fieldKey) ?? []), note]);
        }
        return result;
    }

    // Every note inside one entry, fields included - for the collapsed header
    countForItem(sectionKey: string, itemId: string): number {
        return this.notes().filter(note => note.sectionKey === sectionKey && note.itemId === itemId).length;
    }

    // Entries removed since the request was made; a note must not vanish with its anchor
    orphans(sectionKey: string, liveItemIds: ReadonlySet<string>): PendingNoteResponse[] {
        return this.notes().filter(note =>
            note.sectionKey === sectionKey && !!note.itemId && !liveItemIds.has(note.itemId)
        );
    }
}