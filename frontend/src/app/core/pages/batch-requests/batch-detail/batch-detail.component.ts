import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { ActivatedRoute, Router } from "@angular/router";
import { BatchRequestService } from "../../../services/batch-request.service";
import { ToastService } from "../../../services/toast.service";
import { BATCH_STATUS_LABELS, BATCH_TARGET_TYPE_LABELS, BatchRequestStatus, BatchRouteParam } from "../../../enums/batch-request.enum";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { BatchCancelResponse, BatchFailedItemResponse, BatchRequestResponse } from "../../../dtos/batch-request.dto";
import { BatchPageState } from "../../../models/batch-request.model";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { batchPercent, batchShortId, batchTargetText } from "../../../utils/batch-progress-util";
import { AppRoute } from "../../../enums/app-route.enum";
import { MatTooltipModule } from "@angular/material/tooltip";
import { BatchCancelDialogComponent } from "../batch-cancel-dialog/batch-cancel-dialog.component";
import { UpdateRequestService } from "../../../services/update-request.service";
import { UPDATE_REQUEST_STATUS_LABELS, UpdateRequestStatus } from "../../../enums/update-request.enum";
import { UpdateRequestResponse } from "../../../dtos/update-request.dto";
import { SortDirection, UpdateRequestSortField } from "../../../enums/sort-field.enum";
import { HttpErrorResponse, HttpStatusCode } from "@angular/common/http";
import { AuthService } from "../../../services/auth.service";
import { UserRole } from "../../../enums/user-role.enum";

/*
 * Progress of one batch, its failed emails and the requests it created.
 * - Polled while PROCESSING with setTimeout, so a slow answer never stacks a second request.
 * - The whole batch is cancelled through the shared dialog; one request at a time from its row.
 */
@Component({
    selector: 'app-batch-detail',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, DatePipe, BatchCancelDialogComponent],
    templateUrl: './batch-detail.component.html',
    styleUrl: './batch-detail.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BatchDetailComponent implements OnInit {

    private static readonly POLL_INTERVAL_MS = 3000;
    private static readonly SLOW_POLL_INTERVAL_MS = 15000;
    // Past this the page warns and polls less often
    private static readonly SLOW_AFTER_MS = 5 * 60 * 1000;
    private static readonly DEFAULT_PAGE_SIZE = 10;
    private static readonly CLOSE_DELAY_MS = 500;

    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly batchService = inject(BatchRequestService);
    private readonly updateRequestService = inject(UpdateRequestService);
    private readonly toast = inject(ToastService);
    private readonly auth = inject(AuthService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly statusLabels = BATCH_STATUS_LABELS;
    readonly targetTypeLabels = BATCH_TARGET_TYPE_LABELS;
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly childStatusLabels = UPDATE_REQUEST_STATUS_LABELS;
    readonly status = BatchRequestStatus;
    readonly childStatus = UpdateRequestStatus;
    readonly targetText = batchTargetText;

    // ---------- Batch ----------
    readonly batch = signal<BatchRequestResponse | null>(null);
    readonly loading = signal(true);
    readonly resending = signal(false);
    readonly slowWarning = signal(false);
    readonly cancelOpen = signal(false);

    // ---------- Failed emails ----------
    readonly failedItems = signal<BatchFailedItemResponse[]>([]);
    readonly failedLoading = signal(false);
    readonly failedPage = signal<BatchPageState>({
        index: 0,
        size: BatchDetailComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    // ---------- Requests in the batch ----------
    readonly children = signal<UpdateRequestResponse[]>([]);
    readonly childrenLoading = signal(false);
    readonly childrenPage = signal<BatchPageState>({
        index: 0,
        size: BatchDetailComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });
    readonly childCancelTarget = signal<UpdateRequestResponse | null>(null);
    readonly childCancelling = signal(false);
    readonly isChildCancelClosing = signal(false);
    private childBackdropMouseDownTarget: EventTarget | null = null;

    readonly isProcessing = computed(() => this.batch()?.status === BatchRequestStatus.Processing);
    readonly isCancelled = computed(() => this.batch()?.status === BatchRequestStatus.Cancelled);

    readonly percent = computed(() => {
        const batch = this.batch();
        return batch ? batchPercent(batch) : 0;
    });

    // Only a finished batch can be resent; a cancelled one never reopens
    readonly canResend = computed(() => {
        const batch = this.batch();
        const finished = batch?.status === BatchRequestStatus.Completed
            || batch?.status === BatchRequestStatus.CompletedWithErrors;
        return !!batch && finished && batch.errorCount > 0;
    });

    // Children are PENDING, COMPLETED or CANCELLED; the server sends the first two
    readonly cancelledChildren = computed(() => {
        const batch = this.batch();
        return batch ? Math.max(0, batch.totalCount - batch.pendingCount - batch.completedCount) : 0;
    });

    readonly isHr = computed(() => this.auth.hasRole(UserRole.HR));
    readonly canCancelBatch = computed(() => this.batch()?.cancellable ?? false);
    readonly shortId = computed(() => batchShortId(this.batch()?.id ?? this.batchId));

    private batchId = '';
    private pollTimer: ReturnType<typeof setTimeout> | null = null;
    private pollStartedAt: number | null = null;
    private seenErrorCount: number | null = null;

    constructor() {
        this.destroyRef.onDestroy(() => this.stopPolling());
    }

    ngOnInit(): void {
        this.batchId = this.route.snapshot.paramMap.get(BatchRouteParam.Id) ?? '';
        this.load(false);
        this.loadChildren();
    }

    refresh(): void {
        this.stopPolling();
        this.load(false);
        this.loadChildren();
    }

    goToList(): void {
        void this.router.navigate(['/' + AppRoute.BatchRequests]);
    }

    // ---------- Batch actions ----------

    openCancel(): void {
        if (!this.canCancelBatch()) {
            return;
        }
        this.cancelOpen.set(true);
    }

    cancelBatchHint(): string {
        const batch = this.batch();
        if (!batch) {
            return 'Cancel batch request';
        }
        if (batch.cancellable) {
            return 'Cancel this batch request';
        }
        if (batch.status === BatchRequestStatus.Cancelled) {
            return 'This batch request has already been cancelled';
        }
        if (batch.pendingCount === 0) {
            return 'This batch has no pending update requests to cancel';
        }
        if (this.isHr()) {
            return 'HR can only cancel batches they created';
        }
        return 'You do not have permission to cancel this batch';
    }

    resendHint(): string {
        if (this.resending()) {
            return 'Resending failed emails...';
        }
        if (this.batch()?.status === BatchRequestStatus.Processing) {
            return 'This batch is still being processed';
        }
        if (this.batch()?.status === BatchRequestStatus.Cancelled) {
            return 'Cannot resend emails for a cancelled batch';
        }
        return 'Resend failed emails';
    }

    childCancelHint(request: UpdateRequestResponse): string {
        if (request.cancellable) {
            return 'Cancel request';
        }
        if (request.status === UpdateRequestStatus.Completed) {
            return 'Cannot cancel a completed request';
        }
        if (request.status === UpdateRequestStatus.Cancelled) {
            return 'This request has already been cancelled';
        }
        if (this.isHr()) {
            return 'HR can only cancel requests they created';
        }
        return 'You do not have permission to cancel this request';
    }

    // The dialog already showed its toast
    onBatchCancelled(result: BatchCancelResponse): void {
        this.apply(result.batch);
        this.loadChildren();
    }

    onCancelClosed(): void {
        this.cancelOpen.set(false);
    }

    // Only the flagged children go back to the queue; the server refuses while PROCESSING
    resend(): void {
        const batch = this.batch();
        if (!batch || !this.canResend() || this.resending()) {
            return;
        }
        this.resending.set(true);

        this.batchService.resendFailed(batch.id).subscribe({
            next: updated => {
                this.resending.set(false);
                this.toast.success(`Resending ${batch.errorCount} failed email(s)`);
                this.pollStartedAt = null;
                this.apply(updated);
            },
            // The interceptor explains the refusal; reload to show the real state
            error: () => {
                this.resending.set(false);
                this.refresh();
            },
        });
    }

    // ---------- Paging ----------

    onFailedPageChange(event: PageEvent): void {
        this.failedPage.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.loadFailed();
    }

    onChildrenPageChange(event: PageEvent): void {
        this.childrenPage.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.loadChildren();
    }

    // ---------- Cancel one request ----------

    // Offered only where the server set cancellable: Admin any, HR their own batch's requests
    askChildCancel(request: UpdateRequestResponse): void {
        if (!request.cancellable) {
            return;
        }
        this.isChildCancelClosing.set(false);
        this.childCancelTarget.set(request);
    }

    onChildBackdropMouseDown(event: MouseEvent): void {
        this.childBackdropMouseDownTarget = event.target;
    }

    onChildBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.childBackdropMouseDownTarget === event.currentTarget) {
            this.dismissChildCancel();
        }
        this.childBackdropMouseDownTarget = null;
    }

    dismissChildCancel(): void {
        if (this.isChildCancelClosing() || this.childCancelling()) {
            return;
        }
        this.isChildCancelClosing.set(true);
        setTimeout(() => this.closeChildCancel(), BatchDetailComponent.CLOSE_DELAY_MS);
    }

    confirmChildCancel(): void {
        const target = this.childCancelTarget();
        if (!target || this.childCancelling()) {
            return;
        }
        this.childCancelling.set(true);

        this.updateRequestService.cancel(target.id).subscribe({
            next: () => {
                this.closeChildCancel();
                this.toast.success(`Update request cancelled - ${target.employeeName ?? 'the employee'} has been notified`);
                this.afterChildChange();
            },
            // 409: someone closed it first; show its real status
            error: (error: HttpErrorResponse) => {
                this.closeChildCancel();
                if (error.status === HttpStatusCode.Conflict) {
                    this.afterChildChange();
                }
            },
        });
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.childCancelTarget()) {
            this.dismissChildCancel();
        }
    }

    // ---------- Private helpers ----------

    private load(silent: boolean): void {
        this.batchService.getDetail(this.batchId, silent)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: batch => this.apply(batch),
                error: () => {
                    this.loading.set(false);
                    // A dropped poll keeps the loop alive; a failed first load shows the not-found state
                    if (silent && this.isProcessing()) {
                        this.schedulePoll();
                    }
                },
            });
    }

    private apply(batch: BatchRequestResponse): void {
        const wasProcessing = this.isProcessing();
        this.batch.set(batch);
        this.loading.set(false);

        if (batch.errorCount !== this.seenErrorCount) {
            this.seenErrorCount = batch.errorCount;
            this.failedPage.update(state => ({ ...state, index: 0 }));
            this.loadFailed();
        }

        if (batch.status === BatchRequestStatus.Processing) {
            this.schedulePoll();
            return;
        }
        this.stopPolling();
        this.pollStartedAt = null;
        this.slowWarning.set(false);
        // A cancel is announced by its own toast, not as a finished batch
        if (wasProcessing && batch.status !== BatchRequestStatus.Cancelled) {
            this.announceFinished(batch);
        }
    }

    // Counts and the batch's cancel button depend on what is still pending
    private afterChildChange(): void {
        this.loadChildren();
        this.load(true);
    }

    private closeChildCancel(): void {
        this.childCancelTarget.set(null);
        this.childCancelling.set(false);
        this.isChildCancelClosing.set(false);
    }

    private schedulePoll(): void {
        this.stopPolling();
        this.pollStartedAt ??= Date.now();
        const slow = Date.now() - this.pollStartedAt > BatchDetailComponent.SLOW_AFTER_MS;
        this.slowWarning.set(slow);
        this.pollTimer = setTimeout(
            () => this.load(true),
            slow ? BatchDetailComponent.SLOW_POLL_INTERVAL_MS : BatchDetailComponent.POLL_INTERVAL_MS,
        );
    }

    private stopPolling(): void {
        if (this.pollTimer) {
            clearTimeout(this.pollTimer);
            this.pollTimer = null;
        }
    }

    private loadFailed(): void {
        const batch = this.batch();
        if (!batch || batch.errorCount === 0) {
            this.failedItems.set([]);
            this.failedPage.update(state => ({ ...state, index: 0, total: 0 }));
            return;
        }
        const { index, size } = this.failedPage();
        this.failedLoading.set(true);

        this.batchService.getFailedItems(batch.id, { page: index, size })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: page => {
                    this.failedItems.set(page.content);
                    this.failedPage.update(state => ({ ...state, total: page.totalElements }));
                    this.failedLoading.set(false);
                },
                error: () => this.failedLoading.set(false),
            });
    }

    private loadChildren(): void {
        const { index, size } = this.childrenPage();
        this.childrenLoading.set(true);

        this.updateRequestService.list({
            batchId: this.batchId,
            sortBy: UpdateRequestSortField.EmployeeName,
            direction: SortDirection.Asc,
            page: index,
            size,
        }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: page => {
                this.children.set(page.content);
                this.childrenPage.update(state => ({ ...state, total: page.totalElements }));
                this.childrenLoading.set(false);
            },
            error: () => this.childrenLoading.set(false),
        });
    }

    private announceFinished(batch: BatchRequestResponse): void {
        if (batch.errorCount > 0) {
            this.toast.error(`Batch finished - ${batch.errorCount} email(s) could not be delivered`);
            return;
        }
        this.toast.success(`Batch finished - ${batch.totalCount} request(s) sent`);
    }
}