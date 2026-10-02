import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { ActivatedRoute, Router } from "@angular/router";
import { BatchRequestService } from "../../../services/batch-request.service";
import { ToastService } from "../../../services/toast.service";
import { BATCH_STATUS_LABELS, BATCH_TARGET_TYPE_LABELS, BatchRequestStatus, BatchRouteParam } from "../../../enums/batch-request.enum";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { BatchFailedItemResponse, BatchRequestResponse } from "../../../dtos/batch-request.dto";
import { BatchPageState } from "../../../models/batch-request.model";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { batchPercent, batchTargetText } from "../../../utils/batch-progress-util";
import { AppRoute } from "../../../enums/app-route.enum";

/*
 * Progress of one batch, polled while it is PROCESSING.
 * - setTimeout, not setInterval: a slow answer never stacks a second request on top of it.
 * - Polls are silent; a failed poll is simply tried again on the next tick.
 * - The failed list is re-read whenever errorCount changes.
 */
@Component({
    selector: 'app-batch-detail',
    standalone: true,
    imports: [MatPaginatorModule, DatePipe],
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
    private static readonly SHORT_ID_LENGTH = 8;

    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly batchService = inject(BatchRequestService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly statusLabels = BATCH_STATUS_LABELS;
    readonly targetTypeLabels = BATCH_TARGET_TYPE_LABELS;
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly status = BatchRequestStatus;
    readonly targetText = batchTargetText;

    readonly batch = signal<BatchRequestResponse | null>(null);
    readonly loading = signal(true);
    readonly failedItems = signal<BatchFailedItemResponse[]>([]);
    readonly failedLoading = signal(false);
    readonly failedPage = signal<BatchPageState>({
        index: 0,
        size: BatchDetailComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });
    readonly resending = signal(false);
    readonly slowWarning = signal(false);

    readonly isProcessing = computed(() => this.batch()?.status === BatchRequestStatus.Processing);

    // Plan: the bar show 100% once the batch is no longer PROCESSING
    readonly percent = computed(() => {
        const batch = this.batch();
        return batch ? batchPercent(batch) : 0;
    });

    // Plan: only when not PROCESSING and something failed
    readonly canResend = computed(() => {
        const batch = this.batch();
        return !!batch && batch.status !== BatchRequestStatus.Processing && batch.errorCount > 0;
    });

    readonly shortId = computed(() => (this.batch()?.id ?? '').slice(0, BatchDetailComponent.SHORT_ID_LENGTH));

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
    }

    refresh(): void {
        this.stopPolling();
        this.load(false);
    }

    onFailedPageChange(event: PageEvent): void {
        this.failedPage.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.loadFailed();
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
            }
        });
    }

    goToList(): void {
        void this.router.navigate(['/' + AppRoute.BatchRequests]);
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
                    if (silent) {
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
        if (wasProcessing) {
            this.announceFinished(batch);
        }
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

    private announceFinished(batch: BatchRequestResponse): void {
        if (batch.errorCount > 0) {
            this.toast.error(`Batch finished - ${batch.errorCount} email(s) could not be delivered`);
            return;
        }
        this.toast.success(`Batch finished - ${batch.totalCount} request(s) sent`);
    }
}