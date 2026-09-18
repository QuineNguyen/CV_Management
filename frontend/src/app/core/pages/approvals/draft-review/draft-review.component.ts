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

/*
 * Two-column review screen.
 *
 * Left: the draft exactly as its owner last saved it, rendered by the same editor component in
 * read-only mode. Reusing the editor rather than writing a separate viewer is the point - a
 * reviewer must decide on the content the employee sees and two renderers would eventually
 * disagree about spacing, empty fields or ordering.
 * 
 * Right: the context for the decision - who assigned it and why, the deadline and what earlier
 * round concluded. The decision buttons and inline comments land here in the next part of this
 * stage; the column is laid out now so adding them does not reshape the page.
 * 
 * Approving is a one-way step (level 2 publishes), so it always goes through an inline confirm.
 */
@Component({
    selector: 'app-draft-review',
    standalone: true,
    imports: [CvContentEditorComponent, DatePipe],
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

    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly levelLabels = APPROVAL_LEVEL_LABELS;
    readonly levelDescriptions = APPROVAL_LEVEL_DESCRIPTIONS;
    readonly decisionLabels = DECISION_RESULT_LABELS;
    readonly decisionTones = DECISION_RESULT_TONES;

    readonly review = signal<DraftReviewResponse | null>(null);
    readonly loading = signal(true);
    readonly confirmOpen = signal(false);
    readonly approving = signal(false);

    readonly assignment = computed(() => this.review()?.currentAssignment ?? null);
    readonly decisions = computed(() => this.review()?.previousDecisions ?? []);

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
        if (!this.approving()) {
            this.confirmOpen.set(true);
        }
    }

    cancelApprove(): void {
        if (!this.approving()) {
            this.confirmOpen.set(false);
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
                this.confirmOpen.set(false);
                if (DraftReviewComponent.ITEM_GONE_STATUSES.includes(error.status)) {
                    this.backToQueue();
                }
            }
        })
    }
}