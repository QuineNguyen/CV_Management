import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { BatchRequestService } from "../../../services/batch-request.service";
import { Router } from "@angular/router";
import { BATCH_STATUS_LABELS, BATCH_TARGET_TYPE_LABELS, BatchRequestStatus } from "../../../enums/batch-request.enum";
import { batchPercent, batchTargetText } from "../../../utils/batch-progress-util";
import { BatchRequestResponse } from "../../../dtos/batch-request.dto";
import { BatchPageState } from "../../../models/batch-request.model";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AppRoute } from "../../../enums/app-route.enum";
import { BatchCancelDialogComponent } from "../batch-cancel-dialog/batch-cancel-dialog.component";
import { AuthService } from "../../../services/auth.service";
import { UserRole } from "../../../enums/user-role.enum";

/*
 * Every batch, newest first, so a batch can be found again after leaving its detail page.
 * While a batch on the current page is PROCESSING, the page refreshes itself quietly.
 */
@Component({
    selector: 'app-batch-list',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, DatePipe, BatchCancelDialogComponent],
    templateUrl: './batch-list.component.html',
    styleUrl: './batch-list.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BatchListComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;
    private static readonly REFRESH_INTERVAL_MS = 5000;

    private readonly batchService = inject(BatchRequestService);
    private readonly router = inject(Router);
    private readonly auth = inject(AuthService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly statusOptions = Object.values(BatchRequestStatus);
    readonly statusLabels = BATCH_STATUS_LABELS;
    readonly targetTypeLabels = BATCH_TARGET_TYPE_LABELS;
    readonly status = BatchRequestStatus;
    readonly percent = batchPercent;
    readonly targetText = batchTargetText;

    readonly batches = signal<BatchRequestResponse[]>([]);
    readonly loading = signal(false);
    readonly pageState = signal<BatchPageState>({
        index: 0,
        size: BatchListComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });
    readonly statusFilter = signal<BatchRequestStatus | null>(null);
    readonly filterOpen = signal(false);
    readonly cancelTarget = signal<BatchRequestResponse | null>(null);

    readonly isEmpty = computed(() => !this.loading() && this.batches().length === 0);
    readonly isHr = computed(() => this.auth.hasRole(UserRole.HR));

    readonly selectedStatusLabel = computed(() => {
        const status = this.statusFilter();
        return status ? this.statusLabels[status] : 'All statuses';
    });

    private refreshTimer: ReturnType<typeof setTimeout> | null = null;

    constructor() {
        this.destroyRef.onDestroy(() => this.stopRefresh());
    }

    ngOnInit(): void {
        this.load(false);
    }

    // ---------- Loading ----------

    load(silent: boolean): void {
        this.stopRefresh();
        if (!silent) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();

        this.batchService.list({ status: this.statusFilter() ?? undefined, page: index, size }, silent)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: page => {
                    this.batches.set(page.content);
                    this.pageState.update(state => ({ ...state, total: page.totalElements }));
                    this.loading.set(false);
                    this.scheduleRefresh();
                },
                error: () => {
                    this.loading.set(false);
                    // A dropped background refresh keeps the loop alive
                    if (silent) {
                        this.scheduleRefresh();
                    }
                },
            });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.load(false);
    }

    // ---------- Filter ----------

    toggleFilter(event: MouseEvent): void {
        event.stopPropagation();
        this.filterOpen.update(open => !open);
    }

    selectStatus(status: BatchRequestStatus | null): void {
        this.statusFilter.set(status);
        this.filterOpen.set(false);
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.load(false);
    }

    @HostListener('document:click')
    @HostListener('document:keydown.escape')
    closeFilter(): void {
        if (this.filterOpen()) {
            this.filterOpen.set(false);
        }
    }

    // ---------- Cancel ----------

    // Offered only where the server set cancellable
    openCancel(batch: BatchRequestResponse): void {
        if (!batch.cancellable) {
            return;
        }
        this.cancelTarget.set(batch);
    }

    cancelBatchHint(batch: BatchRequestResponse): string {
        if (batch.cancellable) {
            return 'Cancel batch';
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

    // The dialog already showed its toast; the row needs its new status and counts
    onCancelled(): void {
        this.load(false);
    }

    onCancelClosed(): void {
        this.cancelTarget.set(null);
    }

    // ---------- Navigation ----------

    openCreate(): void {
        void this.router.navigate(['/' + AppRoute.BatchCreate]);
    }

    openDetail(batch: BatchRequestResponse): void {
        void this.router.navigate(['/' + AppRoute.BatchRequests, batch.id]);
    }

    // ---------- Private helpers ----------

    // Only while something on this page can still move
    private scheduleRefresh(): void {
        if (!this.batches().some(batch => batch.status === BatchRequestStatus.Processing)) {
            return;
        }
        this.refreshTimer = setTimeout(() => this.load(true), BatchListComponent.REFRESH_INTERVAL_MS);
    }

    private stopRefresh(): void {
        if (this.refreshTimer) {
            clearTimeout(this.refreshTimer);
            this.refreshTimer = null;
        }
    }
}