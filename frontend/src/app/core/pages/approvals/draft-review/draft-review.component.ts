import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal } from "@angular/core";
import { CvContentEditorComponent } from "../../cvs/cv-content-editor/cv-content-editor.component";
import { DatePipe } from "@angular/common";
import { ApprovalService } from "../../../services/approval.service";
import { ActivatedRoute, Router } from "@angular/router";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { APPROVAL_LEVEL_DESCRIPTIONS, APPROVAL_LEVEL_LABELS, ApprovalLevel } from "../../../enums/approval-level.enum";
import { DECISION_RESULT_LABELS, DECISION_RESULT_TONES } from "../../../enums/decision-result.enum";
import { DraftReviewResponse } from "../../../dtos/approval.dto";
import { slaLabelOf, slaToneOf } from "../../../models/approval-queue.model";
import { AppRoute } from "../../../enums/app-route.enum";
import { HttpErrorResponse, HttpStatusCode } from "@angular/common/http";
import { ToastService } from "../../../services/toast.service";
import { AnchorPickerComponent } from "../anchor-picker/anchor-picker.component";
import { ReviewDecisionMode } from "../../../enums/review-decision-mode.enum";
import { INLINE_COMMENT_MAX_LENGTH, PendingInlineComment } from "../../../models/inline-comment.model";

/*
 * Two-column review screen.
 *
 * Left: the draft exactly as its owner last saved it, rendered by the same editor component in
 * read-only mode, with the comment threads of earlier rounds pinned at their anchors.
 * 
 * Right: the context for the decision - who assigned it and why, the deadline, what earlier
 * rounds concluded - and the decision card: approve or reject with an overall reason plus
 * optional comments anchored to (section, entry, field).
 * 
 * Both decisions are one-way, so approve goes through an inline confirm and reject through a
 * form whose submit stays disabled until a reason is written.
 */
@Component({
    selector: 'app-draft-review',
    standalone: true,
    imports: [CvContentEditorComponent, DatePipe, AnchorPickerComponent],
    templateUrl: './draft-review.component.html',
    styleUrl: './draft-review.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DraftReviewComponent implements OnInit {

    // Errors meaning the draft is no longer this reviewer's to decide.
    private static readonly ITEM_GONE_STATUSES: readonly HttpStatusCode[] = [
        HttpStatusCode.Forbidden,
        HttpStatusCode.NotFound,
        HttpStatusCode.Conflict,
    ];

    private readonly approvalService = inject(ApprovalService);
    private readonly toast = inject(ToastService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);

    readonly Mode = ReviewDecisionMode;
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly levelLabels = APPROVAL_LEVEL_LABELS;
    readonly levelDescriptions = APPROVAL_LEVEL_DESCRIPTIONS;
    readonly decisionLabels = DECISION_RESULT_LABELS;
    readonly decisionTones = DECISION_RESULT_TONES;
    readonly reasonMaxLength = INLINE_COMMENT_MAX_LENGTH;

    readonly review = signal<DraftReviewResponse | null>(null);
    readonly loading = signal(true);

    readonly mode = signal(ReviewDecisionMode.Idle);
    readonly approving = signal(false);
    readonly rejecting = signal(false);
    readonly busy = computed(() => this.approving() || this.rejecting());

    readonly confirmOpen = computed(() => this.mode() === ReviewDecisionMode.ConfirmApprove);
    readonly rejectFormOpen = computed(() => this.mode() === ReviewDecisionMode.Rejecting);

    // ---------- Reject form state ----------
    readonly rejectReason = signal('');
    readonly pendingComments = signal<PendingInlineComment[]>([]);
    readonly pickerOpen = signal(false);
    readonly canSubmitReject = computed(() => this.rejectReason().trim().length > 0 && !this.rejecting());

    readonly assignment = computed(() => this.review()?.currentAssignment ?? null);
    readonly decisions = computed(() => this.review()?.previousDecisions ?? []);
    readonly inlineComments = computed(() => this.review()?.draft.inlineComments ?? []);
    readonly earlierThreadCount = computed(() => this.inlineComments().filter(c => !c.parentCommentId).length);

    // Level 2 is the last step: approving it publishes a new version
    readonly isFinalLevel = computed(() => this.assignment()?.level === ApprovalLevel.Level2);
    readonly approveLabel = computed(() => this.isFinalLevel() ? 'Approve & publish' : 'Approve');

    readonly slaTone = computed(() => {
        const assignment = this.assignment();
        return assignment ? slaToneOf(assignment.slaRemainingMinutes) : '';
    });

    readonly slaLabel = computed(() => {
        const assignment = this.assignment();
        return assignment ? slaLabelOf(assignment.slaRemainingMinutes) : '';
    });

    ngOnInit(): void {
        const draftId = this.route.snapshot.paramMap.get('draftId');
        if (draftId) {
            this.load(draftId);
        } else {
            this.loading.set(false);
        }
    }

    private load(draftId: string): void {
        this.approvalService.openForReview(draftId).subscribe({
            next: review => {
                this.review.set(review);
                this.loading.set(false);
            },
            /*
             * A 403 here means the assignment moved to somebody else while this page was opening.
             * The interceptor says so; returning to the queue is the only useful next step, since
             * an empty review screen would invite a second attempt at the same dead end.
             */
            error: () => {
                this.loading.set(false);
                void this.router.navigate(['/' + AppRoute.ApprovalQueue]);
            },
        });
    }

    backToQueue(): void {
        void this.router.navigate(['/' + AppRoute.ApprovalQueue]);
    }

    // ---------- Approve ----------

    requestApprove(): void {
        if (!this.busy()) {
            this.mode.set(ReviewDecisionMode.ConfirmApprove);
        }
    }

    cancelApprove(): void {
        if (!this.approving()) {
            this.mode.set(ReviewDecisionMode.Idle);
        }
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.confirmOpen()) {
            this.cancelApprove();
        }
    }

    confirmApprove(): void {
        const draftId = this.review()?.draft.id;
        if (!draftId || this.approving()) {
            return;
        }

        // Captured now: the label must match the level that was approved.
        const finalLevel = this.isFinalLevel();
        this.approving.set(true);

        this.approvalService.approve(draftId).subscribe({
            next: () => {
                this.toast.success(finalLevel
                    ? 'Approved and published as a new version'
                    : 'Approved and forwarded to HR for format review'
                );
                this.backToQueue();
            },
            // The interceptor shows the reason; a draft that moved on is no longer ours to decide.
            error: (error: HttpErrorResponse) => {
                this.approving.set(false);
                this.mode.set(ReviewDecisionMode.Idle);
                this.leaveIfGone(error);
            }
        });
    }

    // ---------- Reject ----------

    startReject(): void {
        if (!this.busy()) {
            this.mode.set(ReviewDecisionMode.Rejecting);
        }
    }

    cancelReject(): void {
        if (this.rejecting()) {
            return;
        }
        this.mode.set(ReviewDecisionMode.Idle);
        this.rejectReason.set('');
        this.pendingComments.set([]);
        this.pickerOpen.set(false);
    }

    onReasonInput(event: Event): void {
        this.rejectReason.set((event.target as HTMLTextAreaElement).value);
    }

    togglePicker(): void {
        this.pickerOpen.update(open => !open);
    }

    addPendingComment(comment: PendingInlineComment): void {
        this.pendingComments.update(list => [...list, comment]);
        this.pickerOpen.set(false);
    }

    removePendingComment(localId: string): void {
        this.pendingComments.update(list => list.filter(comment => comment.localId !== localId));
    }

    confirmReject(): void {
        const draftId = this.review()?.draft.id;
        if (!draftId || !this.canSubmitReject() || this.approving()) {
            return;
        }
        this.rejecting.set(true);

        this.approvalService.reject(draftId, {
            reason: this.rejectReason().trim(),
            comments: this.pendingComments().map(comment => comment.request),
        }).subscribe({
            next: result => {
                const count = result.commentCount;
                this.toast.success(count
                    ? `Rejected with ${count} ${count === 1 ? 'comment' : 'comments'} and returned to the employee`
                    : 'Rejected and returned to the employee'
                );
                this.backToQueue();
            },
            // A 422 (bad anchor) keeps the form so the reviewer can fix it.
            error: (error: HttpErrorResponse) => {
                this.rejecting.set(false);
                this.leaveIfGone(error);
            },
        });
    }

    private leaveIfGone(error: HttpErrorResponse): void {
        if (DraftReviewComponent.ITEM_GONE_STATUSES.includes(error.status)) {
            this.backToQueue();
        }
    }
}