import { computed, inject, Injectable, signal } from "@angular/core";
import { ApprovalService } from "./approval.service";
import { InlineCommentResponse } from "../dtos/inline-comment.dto";
import { buildThreads, InlineCommentThread } from "../models/inline-comment.model";
import { Observable, tap } from "rxjs";

/*
 * Comment state of one CV editor.
 * - Provided by CvContentEditorComponent, not in root: each editor owns its own threads and
 * section/item editors inject it instead of receiving inputs through four levels.
 * - A reply is appended locally, so the thread updates without reloading the whole draft.
 */
@Injectable()
export class InlineCommentStore {

    private readonly approvalService = inject(ApprovalService);

    readonly comments = signal<InlineCommentResponse[]>([]);
    readonly canReply = signal(false);

    readonly threads = computed(() => buildThreads(this.comments()));

    // itemId null = comments on a SINGLE section.
    threadsFor(sectionKey: string, itemId: string | null): InlineCommentThread[] {
        return this.threads().filter(thread =>
            thread.root.sectionKey === sectionKey && (thread.root.itemId ?? null) === itemId
        );
    }

    reply(parentId: string, content: string): Observable<InlineCommentResponse> {
        return this.approvalService.replyToComment(parentId, { content }).pipe(
            tap(reply => this.comments.update(list => [...list, reply]))
        );
    }
}