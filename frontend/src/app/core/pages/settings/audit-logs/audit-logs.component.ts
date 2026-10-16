import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { DateAdapter, MAT_DATE_FORMATS, MatNativeDateModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { EnumLabelPipe } from "../../../utils/enum-label.util";
import { CustomDateAdapter, DD_MM_YYYY_FORMATS } from "../../../utils/app-date-adapter.util";
import { SortState } from "../../../models/sort-state.model";
import { AriaSortDirection, AuditLogSortField, SortDirection, SortIcon, UserSortField } from "../../../enums/sort-field.enum";
import { AuditLogService } from "../../../services/audit-log.service";
import { UserService } from "../../../services/user.service";
import { ToastService } from "../../../services/toast.service";
import { ROLE_LABELS } from "../../../models/user.model";
import { AuditLogResponse } from "../../../dtos/audit-log.dto";
import { AuditActorOption, AuditLogPageState } from "../../../models/audit-log-page.model";
import { catchError, debounceTime, EMPTY, map, of, Subject, switchMap } from "rxjs";
import { actionToneOf, toIsoDate } from "../../../utils/audit-log.util";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AuditActionTone } from "../../../enums/audit-log.enum";
import { AuditLogDetailDialogComponent } from "./audit-log-detail-dialog/audit-log-detail-dialog.component";

/*
 * Audit trail lookup (Admin). Read-only: every row was written by the business action it
 * describes, inside the same transaction.
 * - Filters apply as soon as they change, like the other list screens.
 * - The list carries no old/new values (they can be whole CV snapshots); the detail dialog
 * fetches them for one entry.
 */
@Component({
    selector: 'app-audit-logs',
    standalone: true,
    imports: [ReactiveFormsModule, MatPaginatorModule, MatTooltipModule, MatDatepickerModule, MatNativeDateModule, DatePipe, EnumLabelPipe, AuditLogDetailDialogComponent],
    providers: [
        { provide: DateAdapter, useClass: CustomDateAdapter },
        { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_FORMATS },
    ],
    templateUrl: './audit-logs.component.html',
    styleUrl: './audit-logs.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditLogsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 20;
    private static readonly SEARCH_DEBOUNCE_MS = 300;
    private static readonly ACTOR_LOOKUP_SIZE = 10;
    private static readonly SHORT_ID_LENGTH = 8;
    // Newest first
    private static readonly DEFAULT_SORT: SortState<AuditLogSortField> = {
        field: AuditLogSortField.CreatedAt,
        direction: SortDirection.Desc,
    };

    private readonly auditLogService = inject(AuditLogService);
    private readonly userService = inject(UserService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [10, 20, 50, 100];
    readonly sortField = AuditLogSortField;
    readonly roleLabels = ROLE_LABELS;
    readonly today = new Date();

    readonly logs = signal<AuditLogResponse[]>([]);
    readonly loading = signal(false);
    readonly pageState = signal<AuditLogPageState>({
        index: 0,
        size: AuditLogsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });
    readonly sort = signal<SortState<AuditLogSortField>>(AuditLogsComponent.DEFAULT_SORT);

    // Filter options come from the server
    readonly actionOptions = signal<string[]>([]);
    readonly targetTypeOptions = signal<string[]>([]);

    // ---------- Filters ----------

    readonly actionFilter = signal<string | null>(null);
    readonly actionOpen = signal(false);
    readonly targetTypeFilter = signal<string | null>(null);
    readonly targetTypeOpen = signal(false);

    readonly actorFilter = signal<AuditActorOption | null>(null);
    readonly actorOpen = signal(false);
    readonly actorKeyword = new FormControl('', { nonNullable: true });
    readonly actorOptions = signal<AuditActorOption[]>([]);
    readonly actorSearching = signal(false);
    private readonly actorQuery = new Subject<string>();

    readonly fromDate = new FormControl<Date | null>(null);
    readonly toDate = new FormControl<Date | null>(null);
    // Mirrors of the date controls, so the pickers can bound each other
    readonly fromDateValue = signal<Date | null>(null);
    readonly toDateValue = signal<Date | null>(null);

    readonly hasFilters = computed(() =>
        !!this.actorFilter() || !!this.actionFilter() || !!this.targetTypeFilter()
        || !!this.fromDateValue() || !!this.toDateValue());

    readonly isEmpty = computed(() => !this.loading() && this.logs().length === 0);

    // ---------- Detail ----------

    readonly detailId = signal<string | null>(null);

    ngOnInit(): void {
        this.loadFilterOptions();
        this.watchActorSearch();
        this.watchDates();
        this.load(true);
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();
        const activeSort = this.sort();
        const from = this.fromDateValue();
        const to = this.toDateValue();

        this.auditLogService.search({
            actorId: this.actorFilter()?.id,
            action: this.actionFilter() ?? undefined,
            targetType: this.targetTypeFilter() ?? undefined,
            fromDate: from ? toIsoDate(from) : undefined,
            toDate: to ? toIsoDate(to) : undefined,
            sortBy: activeSort.field,
            direction: activeSort.direction,
            page: index,
            size,
        }).subscribe({
            next: result => {
                this.logs.set(result.content);
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

    clearFilters(): void {
        this.actorFilter.set(null);
        this.actorKeyword.setValue('', { emitEvent: false });
        this.actionFilter.set(null);
        this.targetTypeFilter.set(null);
        this.fromDate.setValue(null, { emitEvent: false });
        this.toDate.setValue(null, { emitEvent: false });
        this.fromDateValue.set(null);
        this.toDateValue.set(null);
        this.sort.set(AuditLogsComponent.DEFAULT_SORT);
        this.closeDropdowns();
        this.resetToFirstPage();
    }

    // ---------- Action / target type dropdowns ----------

    toggleActionDropdown(event: MouseEvent): void {
        event.stopPropagation();
        const next = !this.actionOpen();
        this.closeDropdowns();
        this.actionOpen.set(next);
    }

    selectAction(action: string | null): void {
        this.actionFilter.set(action);
        this.actionOpen.set(false);
        this.resetToFirstPage();
    }

    toggleTargetTypeDropdown(event: MouseEvent): void {
        event.stopPropagation();
        const next = !this.targetTypeOpen();
        this.closeDropdowns();
        this.targetTypeOpen.set(next);
    }

    selectTargetType(targetType: string | null): void {
        this.targetTypeFilter.set(targetType);
        this.targetTypeOpen.set(false);
        this.resetToFirstPage();
    }

    // ---------- Actor picker ----------

    toggleActorPicker(event: MouseEvent): void {
        event.stopPropagation();
        const next = !this.actorOpen();
        this.closeDropdowns();
        this.actorOpen.set(next);
        if (next) {
            this.actorQuery.next(this.actorKeyword.value.trim());
        }
    }

    selectActor(actor: AuditActorOption | null): void {
        this.actorFilter.set(actor);
        this.actorOpen.set(false);
        this.resetToFirstPage();
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        if (!this.actionOpen() && !this.targetTypeOpen() && !this.actorOpen()) {
            return;
        }
        this.actionOpen.set(false);
        this.targetTypeOpen.set(false);
        this.actorOpen.set(false);
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        // The detail dialog closes itself on Escape
        this.closeDropdowns();
    }

    // ---------- Sorting ----------

    // Time toggles Desc <-> Asc; other columns cycle Asc -> Desc -> newest first
    toggleSort(field: AuditLogSortField): void {
        this.sort.update(current => {
            if (field === AuditLogSortField.CreatedAt) {
                return current.field === field && current.direction === SortDirection.Desc
                    ? { field, direction: SortDirection.Asc }
                    : { field, direction: SortDirection.Desc };
            }
            if (current.field !== field) {
                return { field, direction: SortDirection.Asc };
            }
            return current.direction === SortDirection.Asc
                ? { field, direction: SortDirection.Desc }
                : AuditLogsComponent.DEFAULT_SORT;
        });
        this.resetToFirstPage();
    }

    ariaSort(field: AuditLogSortField): AriaSortDirection {
        const current = this.sort();
        if (current.field !== field) {
            return AriaSortDirection.None;
        }
        return current.direction === SortDirection.Asc ? AriaSortDirection.Ascending : AriaSortDirection.Descending;
    }

    sortIcon(field: AuditLogSortField): SortIcon {
        const current = this.sort();
        if (current.field !== field) {
            return SortIcon.None;
        }
        return current.direction === SortDirection.Asc ? SortIcon.Up : SortIcon.Down;
    }

    // ---------- Rows ----------

    toneOf(action: string): AuditActionTone {
        return actionToneOf(action);
    }

    shortId(id: string): string {
        return id.slice(0, AuditLogsComponent.SHORT_ID_LENGTH);
    }

    async copyId(id: string, event: MouseEvent): Promise<void> {
        // The row itself opens the detail
        event.stopPropagation();
        try {
            await navigator.clipboard.writeText(id);
            this.toast.success('Target ID copied');
        } catch {
            this.toast.error('Could not copy. Open the entry and copy the ID manually');
        }
    }

    openDetail(log: AuditLogResponse): void {
        this.detailId.set(log.id);
    }

    closeDetail(): void {
        this.detailId.set(null);
    }

    // ---------- Internals ----------

    private loadFilterOptions(): void {
        this.auditLogService.listActions()
            .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
            .subscribe(actions => this.actionOptions.set(actions));
        this.auditLogService.listTargetTypes()
            .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
            .subscribe(types => this.targetTypeOptions.set(types));
    }

    // Typing is debounced; opening the picker searches at once. switchMap drops stale answers
    private watchActorSearch(): void {
        this.actorKeyword.valueChanges.pipe(
            debounceTime(AuditLogsComponent.SEARCH_DEBOUNCE_MS),
            map(value => value.trim()),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(keyword => this.actorQuery.next(keyword));

        this.actorQuery.pipe(
            switchMap(keyword => {
                this.actorSearching.set(true);
                return this.userService.search({
                    keyword: keyword || undefined,
                    sortBy: UserSortField.FullName,
                    direction: SortDirection.Asc,
                    page: 0,
                    size: AuditLogsComponent.ACTOR_LOOKUP_SIZE,
                }).pipe(
                    map(page => page.content.map(user => ({ id: user.id, fullName: user.fullName, email: user.email }))),
                    catchError(() => of<AuditActorOption[]>([])),
                );
            }),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(options => {
            this.actorOptions.set(options);
            this.actorSearching.set(false);
        });
    }

    private watchDates(): void {
        this.fromDate.valueChanges
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(value => {
                this.fromDateValue.set(value);
                this.resetToFirstPage();
            });
        this.toDate.valueChanges
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(value => {
                this.toDateValue.set(value);
                this.resetToFirstPage();
            });
    }

    private resetToFirstPage(): void {
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.load(true);
    }
}