import { DatePipe, NgTemplateOutlet } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from "@angular/core";
import { InlineCommentStore } from "../../../services/inline-comment-store.service";
import { ToastService } from "../../../services/toast.service";
import { INLINE_COMMENT_STATUS_LABELS, InlineCommentStatus } from "../../../enums/inline-comment-status.enum";
import { fieldLabelOf, INLINE_COMMENT_MAX_LENGTH, InlineCommentThread, isOpenThread } from "../../../models/inline-comment.model";

/*
 * Renders the threads pinned to one anchor (a section or an entry).
 * - Open threads first; resolved ones from earlier rounds start collapsed.
 * - The reply box is plain signal state, not a form control, so it stays usable while the
 * surrounding CV form is disabled for review.
 */
@Component({
    selector: 'app-inline-comment-thread',
    standalone: true,
    imports: [DatePipe, NgTemplateOutlet],
    templateUrl: './inline-comment-thread.component.html',
    styleUrl: './inline-comment-thread.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InlineCommentThreadComponent {

    private readonly store = inject(InlineCommentStore);
    private readonly toast = inject(ToastService);

    readonly threads = input.required<readonly InlineCommentThread[]>();

    readonly maxLength = INLINE_COMMENT_MAX_LENGTH;
    readonly canReply = this.store.canReply;

    readonly openThreads = computed(() => this.threads().filter(isOpenThread));
    readonly resolvedThreads = computed(() => this.threads().filter(thread => !isOpenThread(thread)));

    readonly showResolved = signal(false);
    readonly replyingTo = signal<string | null>(null);
    readonly replyText = signal("");
    readonly sending = signal(false);
    readonly canSend = computed(() => this.replyText().trim().length > 0 && !this.sending());

    /*
     * The three helpers below exist because ngTemplateOutlet hands the template an untyped
     * context: doing the lookups here keeps them type-checked.
     */
    isOpen(thread: InlineCommentThread): boolean {
        return isOpenThread(thread);
    }

    statusLabel(thread: InlineCommentThread): string {
        return INLINE_COMMENT_STATUS_LABELS[thread.root.status];
    }
    
    fieldLabel(thread: InlineCommentThread): string | null {
        return fieldLabelOf(thread.root.sectionKey, thread.root.fieldKey);
    }

    toggleResolved(): void {
        this.showResolved.update(shown => !shown);
    }

    startReply(rootId: string): void {
        this.replyingTo.set(rootId);
        this.replyText.set("");
    }

    cancelReply(): void {
        if (!this.sending()) {
            this.replyingTo.set(null);
        }
    }

    onReplyInput(event: Event): void {
        this.replyText.set((event.target as HTMLTextAreaElement).value);
    }

    sendReply(thread: InlineCommentThread): void {
        if (!this.canSend()) {
            return;
        }
        this.sending.set(true);

        this.store.reply(thread.root.id, this.replyText().trim()).subscribe({
            next: () => {
                this.sending.set(false);
                this.replyingTo.set(null);
                this.replyText.set("");
                this.toast.success("Reply posted");
            },
            // The error interceptor shows the reason.
            error: () => this.sending.set(false),
        });
    }
}