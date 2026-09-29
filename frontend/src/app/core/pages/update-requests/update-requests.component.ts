import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { DateAdapter, MAT_DATE_FORMATS, MatNativeDateModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CustomDateAdapter, DD_MM_YYYY_FORMATS } from "../../utils/app-date-adapter.util";
import { SortState } from "../../models/sort-state.model";
import { AriaSortDirection, SortDirection, SortIcon, UpdateRequestSortField } from "../../enums/sort-field.enum";
import { UpdateRequestService } from "../../services/update-request.service";
import { DepartmentService } from "../../services/department.service";
import { AuthService } from "../../services/auth.service";
import { Router } from "@angular/router";
import { UPDATE_REQUEST_STATUS_LABELS, UpdateRequestFilter, UpdateRequestStatus } from "../../enums/update-request.enum";
import { CV_LANGUAGE_LABELS, CV_LANGUAGE_ORDER, CvLanguage } from "../../enums/cv-language.enum";
import { AnchoredNoteResponse, UpdateRequestResponse } from "../../dtos/update-request.dto";
import { DepartmentNode } from "../../dtos/department.dto";
import { UpdateRequestPageState } from "../../models/update-request.model";
import { UserRole } from "../../enums/user-role.enum";
import { toIsoDate } from "../../utils/iso-date.util";
import { catchError, EMPTY } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AppRoute } from "../../enums/app-route.enum";
import { QueryParam } from "../../enums/query-param.enum";
import { CV_SECTIONS } from "../../models/cv-section-descriptor.model";
import { CreateUpdateRequestDialogComponent } from "./create-update-request-dialog/create-update-request-dialog.component";

/*
 * One list for every role; the server narrows rows to the caller.
 * - Admin/HR: every request and the only ones who create.
 * - Tech Lead: their own requests and those of the teams they lead, read-only.
 * - Employee: requests addressed to them, with a shortcut to the CV each one points at.
 */
@Component({
    selector: 'app-update-requests',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, MatDatepickerModule, MatNativeDateModule, DatePipe, CreateUpdateRequestDialogComponent, ],
    providers: [
        { provide: DateAdapter, useClass: CustomDateAdapter },
        { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_FORMATS },
    ],
    templateUrl: './update-requests.component.html',
    styleUrl: './update-requests.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpdateRequestsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;
    private static readonly DEPARTMENT_LOOKUP_SIZE = 100;
    private static readonly CLOSE_DELAY_MS = 500;
    private static readonly EDIT_SEGMENT = 'edit';
    private static readonly ENTRY_LABEL = 'Entry';

    private static readonly DEFAULT_SORT: SortState<UpdateRequestSortField> = {
        field: UpdateRequestSortField.CreatedAt,
        direction: SortDirection.Desc,
    };

    // First click on a column: newest request, soonest deadline, name A-Z
    private static readonly FIRST_DIRECTION: Record<UpdateRequestSortField, SortDirection> = {
        [UpdateRequestSortField.CreatedAt]: SortDirection.Desc,
        [UpdateRequestSortField.Deadline]: SortDirection.Asc,
        [UpdateRequestSortField.EmployeeName]: SortDirection.Asc,
    };

    private readonly updateRequestService = inject(UpdateRequestService);
    private readonly departmentService = inject(DepartmentService);
    private readonly auth = inject(AuthService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly statusLabels = UPDATE_REQUEST_STATUS_LABELS;
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly statusOptions = Object.values(UpdateRequestStatus);
    readonly languageOptions = CV_LANGUAGE_ORDER;
    readonly sortField = UpdateRequestSortField;
    readonly filterKey = UpdateRequestFilter;
    readonly pendingStatus = UpdateRequestStatus.Pending;
    readonly completedStatus = UpdateRequestStatus.Completed;

    readonly requests = signal<UpdateRequestResponse[]>([]);
    readonly departments = signal<DepartmentNode[]>([]);
    readonly loading = signal(false);
    readonly pageState = signal<UpdateRequestPageState>({
        index: 0,
        size: UpdateRequestsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    // ---------- Filters and sort ----------
    readonly statusFilter = signal<UpdateRequestStatus | null>(null);
    readonly languageFilter = signal<CvLanguage | null>(null);
    readonly departmentFilter = signal<string | null>(null);
    readonly fromDate = signal<Date | null>(null);
    readonly toDate = signal<Date | null>(null);
    readonly openFilter = signal<UpdateRequestFilter | null>(null);
    readonly sort = signal<SortState<UpdateRequestSortField>>(UpdateRequestsComponent.DEFAULT_SORT);

    // ---------- Dialogs ----------
    readonly createOpen = signal(false);
    readonly detailTarget = signal<UpdateRequestResponse | null>(null);
    readonly isDetailClosing = signal(false);
    private detailBackdropMouseDownTarget: EventTarget | null = null;

    // Only Admin and HR send requests
    readonly canCreate = computed(() => this.auth.hasRole(UserRole.Admin, UserRole.HR));

    // Departments span the company, so the filter is for company-wide viewers
    readonly canFilterByDepartment = computed(() => this.auth.hasRole(UserRole.Admin, UserRole.HR));

    // An employee only sees requests addressed to them, so the Employee column adds nothing
    readonly isEmployeeView = computed(() => this.auth.hasRole(UserRole.Employee));
    readonly isTechLeadView = computed(() => this.auth.hasRole(UserRole.TechLead));
    readonly currentUserId = computed(() => this.auth.user()?.id ?? null);
    readonly isEmpty = computed(() => !this.loading() && this.requests().length === 0);

    readonly selectedStatusLabel = computed(() => {
        const status = this.statusFilter();
        return status ? this.statusLabels[status] : 'All statuses';
    });

    readonly selectedLanguageLabel = computed(() => {
        const language = this.languageFilter();
        return language ? this.languageLabels[language] : 'All languages';
    });

    readonly selectedDepartmentLabel = computed(() => {
        const id = this.departmentFilter();
        const department = id ? this.departments().find(item => item.id === id) : null;
        return department ? `${department.code} - ${department.name}` : 'All departments';
    });

    ngOnInit(): void {
        if (this.canFilterByDepartment()) {
            this.loadDepartments();
        }
        this.load(true);
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();
        const activeSort = this.sort();

        this.updateRequestService.list({
            status: this.statusFilter() ?? undefined,
            departmentId: this.departmentFilter() ?? undefined,
            language: this.languageFilter() ?? undefined,
            fromDate: toIsoDate(this.fromDate()) ?? undefined,
            toDate: toIsoDate(this.toDate()) ?? undefined,
            sortBy: activeSort.field,
            direction: activeSort.direction,
            page: index,
            size,
        }).subscribe({
            next: result => {
                this.requests.set(result.content);
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

    // ---------- Filters ----------

    toggleFilter(filter: UpdateRequestFilter, event: MouseEvent): void {
        event.stopPropagation();
        this.openFilter.update(open => open === filter ? null : filter);
    }

    selectStatus(status: UpdateRequestStatus | null): void {
        this.statusFilter.set(status);
        this.openFilter.set(null);
        this.resetToFirstPage();
    }

    selectLanguage(language: CvLanguage | null): void {
        this.languageFilter.set(language);
        this.openFilter.set(null);
        this.resetToFirstPage();
    }

    selectDepartment(id: string | null): void {
        this.departmentFilter.set(id);
        this.openFilter.set(null);
        this.resetToFirstPage();
    }

    onFromDate(date: Date | null): void {
        this.fromDate.set(date);
        this.resetToFirstPage();
    }

    onToDate(date: Date | null): void {
        this.toDate.set(date);
        this.resetToFirstPage();
    }

    clearFilters(): void {
        this.statusFilter.set(null);
        this.languageFilter.set(null);
        this.departmentFilter.set(null);
        this.fromDate.set(null);
        this.toDate.set(null);
        this.sort.set(UpdateRequestsComponent.DEFAULT_SORT);
        this.openFilter.set(null);
        this.resetToFirstPage();
    }

    @HostListener('document:click')
    closeFilters(): void {
        if (this.openFilter()) {
            this.openFilter.set(null);
        }
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.openFilter()) {
            this.openFilter.set(null);
            return;
        }
        if (this.detailTarget()) {
            this.closeDetail();
        }
    }

    // ---------- Sort ----------

    // Same column flips the direction; a new column starts from its natural order
    toggleSort(field: UpdateRequestSortField): void {
        this.sort.update(current => current.field === field
            ? { field, direction: current.direction === SortDirection.Asc ? SortDirection.Desc : SortDirection.Asc }
            : { field, direction: UpdateRequestsComponent.FIRST_DIRECTION[field] }
        );
        this.resetToFirstPage();
    }

    ariaSort(field: UpdateRequestSortField): AriaSortDirection {
        const current = this.sort();
        if (current.field !== field) {
            return AriaSortDirection.None;
        }
        return current.direction === SortDirection.Asc ? AriaSortDirection.Ascending : AriaSortDirection.Descending;
    }

    sortIcon(field: UpdateRequestSortField): SortIcon {
        const current = this.sort();
        if (current.field !== field) {
            return SortIcon.None;
        }
        return current.direction === SortDirection.Asc ? SortIcon.Up : SortIcon.Down;
    }

    // ---------- Create dialog ----------

    openCreate(): void {
        this.createOpen.set(true);
    }

    // The dialog already showed its toast; the list only needs the new rows
    onCreated(): void {
        this.resetToFirstPage();
    }

    onCreateClosed(): void {
        this.createOpen.set(false);
    }

    // ---------- Detail modal ----------

    openDetail(request: UpdateRequestResponse): void {
        this.isDetailClosing.set(false);
        this.detailTarget.set(request);
    }

    onDetailBackdropMouseDown(event: MouseEvent): void {
        this.detailBackdropMouseDownTarget = event.target;
    }

    onDetailBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.detailBackdropMouseDownTarget === event.currentTarget) {
            this.closeDetail();
        }
        this.detailBackdropMouseDownTarget = null;
    }

    closeDetail(): void {
        if (this.isDetailClosing()) {
            return;
        }
        this.isDetailClosing.set(true);
        setTimeout(() => {
            this.detailTarget.set(null);
            this.isDetailClosing.set(false);
        }, UpdateRequestsComponent.CLOSE_DELAY_MS);
    }

    // ---------- Row helpers ----------

    isOverdue(request: UpdateRequestResponse): boolean {
        return request.status === UpdateRequestStatus.Pending && new Date(request.deadline).getTime() < Date.now();
    }

    // Only the addressee acts on a request and only while it is still open
    canUpdateCv(request: UpdateRequestResponse): boolean {
        return request.status === UpdateRequestStatus.Pending && request.employeeId === this.currentUserId();
    }

    updateCv(request: UpdateRequestResponse): void {
        if (request.cvId) {
            void this.router.navigate(['/' + AppRoute.Cvs, request.cvId, UpdateRequestsComponent.EDIT_SEGMENT]);
            return;
        }
        // No CV yet: the create screen, preselected as far as the request knows
        void this.router.navigate(['/' + AppRoute.CvsNew], {
            queryParams: {
                [QueryParam.ProfileId]: request.profileId ?? undefined,
                [QueryParam.Language]: request.language,
            },
        });
    }

    noteAnchorLabel(note: AnchoredNoteResponse): string {
        const section = CV_SECTIONS.find(item => item.key === note.sectionKey);
        const field = section?.fields.find(item => item.key === note.fieldKey)?.label ?? note.fieldKey;
        const entry = note.itemId ? UpdateRequestsComponent.ENTRY_LABEL : null;

        return [section?.label ?? note.sectionKey, entry, field]
            .filter((part): part is string => !!part)
            .join(' › ');
    }

    // ---------- Private helpers ----------

    private loadDepartments(): void {
        this.departmentService.search({
            page: 0,
            size: UpdateRequestsComponent.DEPARTMENT_LOOKUP_SIZE,
        }).pipe(
            catchError(() => EMPTY),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => this.departments.set(page.content));
    }

    private resetToFirstPage(): void {
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.load(true);
    }
}