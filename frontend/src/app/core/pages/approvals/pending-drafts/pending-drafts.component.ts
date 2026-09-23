import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ApprovalService } from "../../../services/approval.service";
import { ToastService } from "../../../services/toast.service";
import { AdminDraftAction } from "../../../enums/admin-draft-action.enum";
import { CV_LANGUAGE_LABELS, CvLanguage } from "../../../enums/cv-language.enum";
import { APPROVAL_LEVEL_LABELS } from "../../../enums/approval-level.enum";
import { DRAFT_STATUS_LABELS } from "../../../enums/draft-status.enum";
import { ROLE_LABELS } from "../../../models/user.model";
import { PendingDraftItem, ReassignCandidate } from "../../../dtos/approval.dto";
import { ApprovalQueuePageState, slaLabelOf, slaToneOf } from "../../../models/approval-queue.model";
import { PendingDraftSortField, SortDirection } from "../../../enums/sort-field.enum";

/*
 * The administrator's oversight list.
 *
 * Deliberately the opposite of the approval queue: that one is scoped to a single assignee because
 * exclusive assignment is what stops two reviewers deciding the same CV, while this one shows every
 * draft in flight because the two actions offered here - cancel and handover - only make sense on
 * work belonging to somebody else. Both actions demand a reason: each stops or moves the work of
 * two other people and that sentence is what they are told.
 */
@Component({
    selector: 'app-pending-drafts',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, DatePipe],
    templateUrl: './pending-drafts.component.html',
    styleUrl: './pending-drafts.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PendingDraftsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;
    private static readonly REASON_MAX_LENGTH = 2000;
    private static readonly CLOSE_ANIMATION_MS = 500;

    private readonly approvalService = inject(ApprovalService);
    private readonly toast = inject(ToastService);

    readonly Action = AdminDraftAction;
    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly levelLabels = APPROVAL_LEVEL_LABELS;
    readonly statusLabels = DRAFT_STATUS_LABELS;
    readonly roleLabels = ROLE_LABELS;
    readonly reasonMaxLength = PendingDraftsComponent.REASON_MAX_LENGTH;

    readonly items = signal<PendingDraftItem[]>([]);
    readonly loading = signal(false);

    readonly pageState = signal<ApprovalQueuePageState>({
        index: 0,
        size: PendingDraftsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly isEmpty = computed(() => !this.loading() && this.items().length === 0);

    readonly overdueCount = computed(
        () => this.items().filter(item => item.slaRemainingMinutes < 0).length
    );

    // ---------- Dialog state ----------
    readonly action = signal(AdminDraftAction.None);
    readonly target = signal<PendingDraftItem | null>(null);
    readonly reason = signal('');
    readonly submitting = signal(false);
    readonly isClosing = signal(false);
    private backdropMouseDownTarget: EventTarget | null = null;

    readonly candidates = signal<ReassignCandidate[]>([]);
    readonly candidatesLoading = signal(false);
    readonly selectedCandidateId = signal<string | null>(null);
    
    readonly canCancel = computed(() => this.reason().trim().length > 0 && !this.submitting());

    readonly canReassign = computed(() =>
        !!this.selectedCandidateId() && this.reason().trim().length > 0 && !this.submitting());

    ngOnInit(): void {
        this.load(true);
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();

        this.approvalService.getPendingDrafts({
            page: index,
            size,
            sortBy: PendingDraftSortField.SubmittedAt,
            direction: SortDirection.Asc,
        }).subscribe({
            // The interceptor already surfaced the reason; the list keeps its previous contents.
            next: result => {
                this.items.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.load(true);
    }

    // ---------- Dialogs ----------

    askCancel(item: PendingDraftItem): void {
        this.resetDialog();
        this.target.set(item);
        this.action.set(AdminDraftAction.Cancel);
    }

    askReassign(item: PendingDraftItem): void {
        this.resetDialog();
        this.target.set(item);
        this.action.set(AdminDraftAction.Reassign);
        this.loadCandidates(item.draftId);
    }

    /*
     * Fetched when the dialog opens rather than with the page: the list is per draft, it changes
     * as workloads move and most rows are never handed over.
     */
    private loadCandidates(draftId: string): void {
        this.candidatesLoading.set(true);

        this.approvalService.getReassignCandidates(draftId).subscribe({
            next: candidates => {
                this.candidates.set(candidates);
                // Least loaded first, so the default pick is already the sensible one.
                this.selectedCandidateId.set(candidates[0]?.userId ?? null);
                this.candidatesLoading.set(false);
            },
            error: () => {
                this.candidatesLoading.set(false);
                this.closeDialog();
            }
        })
    }

    onReasonInput(event: Event): void {
        this.reason.set((event.target as HTMLTextAreaElement).value);
    }

    selectCandidate(userId: string): void {
        this.selectedCandidateId.set(userId);
    }

    onBackdropMouseDown(event: MouseEvent): void {
        this.backdropMouseDownTarget = event.target;
    }

    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.backdropMouseDownTarget === event.currentTarget) {
            this.dismissDialog();
        }
        this.backdropMouseDownTarget = null;
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.action() !== AdminDraftAction.None) {
            this.dismissDialog();
        }
    }

    dismissDialog(): void {
        if (this.isClosing() || this.submitting()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => this.closeDialog(), PendingDraftsComponent.CLOSE_ANIMATION_MS);
    }

    // ---------- Cancel ----------

    confirmCancel(): void {
        const target = this.target();
        if (!target || !this.canCancel()) {
            return;
        }
        this.submitting.set(true);

        this.approvalService.cancelDraft(target.draftId, { reason: this.reason().trim() }).subscribe({
            next: result => {
                this.closeDialog();
                this.toast.success(
                    `Draft of ${target.employeeName ?? 'this employee'} cancelled · `
                    + `${result.assignmentsCancelled} assignment closed, `
                    + `${result.commentsResolved} ${result.commentsResolved === 1 ? 'comment' : 'comments'} resolved`
                );
                this.load(false);
            },
            // A 409 means somebody decided it first; the reload shows the row is gone.
            error: () => {
                this.closeDialog();
                this.load(false);
            },
        });
    }

    // ---------- Reassign ----------

    confirmReassign(): void {
        const target = this.target();
        const newAssigneeId = this.selectedCandidateId();
        if (!target || !newAssigneeId || !this.canReassign()) {
            return;
        }
        this.submitting.set(true);

        this.approvalService.reassign(target.draftId, {
            newAssigneeId,
            reason: this.reason().trim(),
        }).subscribe({
            next: result => {
                this.closeDialog();
                this.toast.success(
                    `Transferred to ${result.newAssigneeName} - the deadline restarts from now`
                );
                this.load(false);
            },
            error: () => {
                this.closeDialog();
                this.load(false);
            },
        });
    }

    // ---------- Presentation ----------

    languageLabel(language: CvLanguage | null): string {
        return language ? this.languageLabels[language] : '-';
    }
    
    slaTone(item: PendingDraftItem): string {
        return slaToneOf(item.slaRemainingMinutes);
    }

    slaLabel(item: PendingDraftItem): string {
        return slaLabelOf(item.slaRemainingMinutes);
    }

    private resetDialog(): void {
        this.reason.set('');
        this.candidates.set([]);
        this.selectedCandidateId.set(null);
        this.submitting.set(false);
        this.isClosing.set(false);
    }

    private closeDialog(): void {
        this.action.set(AdminDraftAction.None);
        this.target.set(null);
        this.resetDialog();
    }
}