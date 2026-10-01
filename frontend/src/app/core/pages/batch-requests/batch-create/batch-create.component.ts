import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { FormControl, ReactiveFormsModule, Validators } from "@angular/forms";
import { DateAdapter, MAT_DATE_FORMATS, MatNativeDateModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { CustomDateAdapter, DD_MM_YYYY_FORMATS } from "../../../utils/app-date-adapter.util";
import { BatchRequestService } from "../../../services/batch-request.service";
import { DepartmentService } from "../../../services/department.service";
import { TeamService } from "../../../services/team.service";
import { UserService } from "../../../services/user.service";
import { ToastService } from "../../../services/toast.service";
import { Router } from "@angular/router";
import { BATCH_WIZARD_STEPS } from "../../../models/batch-request.model";
import { BATCH_EXCLUSION_LABELS, BATCH_TARGET_TYPE_HINTS, BATCH_TARGET_TYPE_ICONS, BATCH_TARGET_TYPE_LABELS, BATCH_TARGET_TYPE_ORDER, BatchErrorCode, BatchPicker, BatchTargetType, BatchWizardStep } from "../../../enums/batch-request.enum";
import { CV_LANGUAGE_LABELS, CV_LANGUAGE_ORDER, CvLanguage } from "../../../enums/cv-language.enum";
import { ROLE_LABELS } from "../../../models/user.model";
import { startOfToday, toIsoDate } from "../../../utils/iso-date.util";
import { DepartmentNode } from "../../../dtos/department.dto";
import { TeamResponse } from "../../../dtos/team.dto";
import { UserResponse } from "../../../dtos/user.dto";
import { BatchPreviewRequest, BatchPreviewResponse } from "../../../dtos/batch-request.dto";
import { catchError, debounceTime, defer, distinctUntilChanged, EMPTY, finalize, map, Observable, of, startWith, switchMap } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AccountStatus } from "../../../enums/account-status.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { HttpErrorResponse } from "@angular/common/http";
import { ApiErrorResponse } from "../../../dtos/api-error.dto";

/*
 * Batch wizard: criteria -> preview -> confirm.
 * - The server recomputes recipients on every preview page and again on create; the screen only
 * sends the criteria and the count it showed, never its own recipient list.
 * - Criteria are snapshotted when the preview runs, so editing them means previewing again.
 */
@Component({
    selector: 'app-batch-create',
    standalone: true,
    imports: [ReactiveFormsModule, MatDatepickerModule, MatNativeDateModule, MatPaginatorModule, DatePipe],
    providers: [
        { provide: DateAdapter, useClass: CustomDateAdapter },
        { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_FORMATS },
    ],
    templateUrl: './batch-create.component.html',
    styleUrl: './batch-create.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BatchCreateComponent implements OnInit {

    private static readonly SEARCH_DEBOUNCE_MS = 300;
    private static readonly EMPLOYEE_LOOKUP_SIZE = 20;
    private static readonly OPTION_LOOKUP_SIZE = 100;
    private static readonly PREVIEW_PAGE_SIZE = 10;
    // Same limits as BatchPreviewRequest on the server
    private static readonly REASON_MAX_LENGTH = 1000;
    private static readonly MAX_EMPLOYEES = 500;

    private readonly batchService = inject(BatchRequestService);
    private readonly departmentService = inject(DepartmentService);
    private readonly teamService = inject(TeamService);
    private readonly userService = inject(UserService);
    private readonly toast = inject(ToastService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    readonly steps = BATCH_WIZARD_STEPS;
    readonly wizardStep = BatchWizardStep;
    readonly targetTypes = BATCH_TARGET_TYPE_ORDER;
    readonly targetTypeKey = BatchTargetType;
    readonly targetTypeLabels = BATCH_TARGET_TYPE_LABELS;
    readonly targetTypeHints = BATCH_TARGET_TYPE_HINTS;
    readonly targetTypeIcons = BATCH_TARGET_TYPE_ICONS;
    readonly picker = BatchPicker;
    readonly exclusionLabels = BATCH_EXCLUSION_LABELS;
    readonly languageOptions = CV_LANGUAGE_ORDER;
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly roleLabels = ROLE_LABELS;
    readonly reasonMaxLength = BatchCreateComponent.REASON_MAX_LENGTH;
    readonly maxEmployees = BatchCreateComponent.MAX_EMPLOYEES;
    readonly pageSize = BatchCreateComponent.PREVIEW_PAGE_SIZE;
    readonly minDeadline = startOfToday();

    readonly step = signal<BatchWizardStep>(BatchWizardStep.Criteria);
    readonly stepIndex = computed(() => this.steps.findIndex(item => item.step === this.step()));

    // ---------- Criteria ----------
    readonly targetType = signal<BatchTargetType>(BatchTargetType.Department);
    readonly departments = signal<DepartmentNode[]>([]);
    readonly teams = signal<TeamResponse[]>([]);
    readonly departmentId = signal<string | null>(null);
    readonly teamId = signal<string | null>(null);
    readonly openPicker = signal<BatchPicker | null>(null);

    readonly keyword = new FormControl('', { nonNullable: true });
    readonly candidates = signal<UserResponse[]>([]);
    readonly searching = signal(false);
    readonly employees = signal<UserResponse[]>([]);
    
    readonly language = signal<CvLanguage | null>(null);
    readonly deadline = signal<Date | null>(null);
    readonly reason = new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(BatchCreateComponent.REASON_MAX_LENGTH)],
    });
    readonly submitAttempted = signal(false);

    // ---------- Preview ----------
    readonly criteria = signal<BatchPreviewRequest | null>(null);
    readonly preview = signal<BatchPreviewResponse | null>(null);
    readonly previewLoading = signal(false);
    readonly includedPage = signal(0);
    readonly excludedPage = signal(0);
    readonly creating = signal(false);
    // Drops answers of an older page request that arrive late
    private previewSeq = 0;

    readonly targetIds = computed<string[]>(() => {
        const type = this.targetType();
        if (type === BatchTargetType.Manual) {
            return this.employees().map(user => user.id);
        }
        const id = type === BatchTargetType.Department ? this.departmentId() : this.teamId();
        return id ? [id] : [];
    });

    readonly selectedDepartmentLabel = computed(() => {
        const department = this.departments().find(item => item.id === this.departmentId());
        return department ? this.departmentLabel(department) : 'Choose a department';
    });

    readonly selectedTeamLabel = computed(() => {
        const team = this.teams().find(item => item.id === this.teamId());
        return team ? this.teamLabel(team) : 'Choose a team';
    });

    readonly employeeTriggerLabel = computed(() => {
        const count = this.employees().length;
        return count ? `${count} employee(s) selected` : 'Search and pick employees';
    })

    readonly includedCount = computed(() => this.preview()?.included.totalElements ?? 0);
    readonly excludedCount = computed(() => this.preview()?.excluded.totalElements ?? 0);

    // Nothing left after exclusion, so "Create batch stays disabled"
    readonly nothingToCreate = computed(() => !!this.preview && this.includedCount() === 0);

    // Built from the snapshot, so the confirm step shows exactly what will be sent
    readonly targetSummary = computed(() => {
        const criteria = this.criteria();
        if (!criteria) {
            return '';
        }
        const [targetId] = criteria.targetIds;
        if (criteria.targetType === BatchTargetType.Department) {
            const department = this.departments().find(item => item.id === targetId);
            return `${department ? this.departmentLabel(department) : '-'}, sub-departments included`;
        }
        if (criteria.targetType === BatchTargetType.Team) {
            const team = this.teams().find(item => item.id === targetId);
            return team ? this.teamLabel(team) : '-';
        }
        return `${criteria.targetIds.length} selected employee(s)`;
    });

    ngOnInit(): void {
        this.loadDepartments();
        this.loadTeams();
        this.watchKeyword();
    }

    // ---------- Criteria ----------

    selectTargetType(type: BatchTargetType): void {
        this.targetType.set(type);
        this.openPicker.set(null);
    }

    togglePicker(picker: BatchPicker, event: MouseEvent): void {
        event.stopPropagation();
        this.openPicker.update(open => (open === picker ? null : picker));
    }

    pickDepartment(id: string): void {
        this.departmentId.set(id);
        this.openPicker.set(null);
    }

    pickTeam(id: string): void {
        this.teamId.set(id);
        this.openPicker.set(null);
    }

    isPicked(user: UserResponse): boolean {
        return this.employees().some(item => item.id === user.id);
    }

    // The picker stays open, so several employees can be ticked in a row
    toggleEmployee(user: UserResponse): void {
        if (this.isPicked(user)) {
            this.removeEmployee(user.id);
            return;
        }
        if (this.employees().length >= this.maxEmployees) {
            this.toast.error(`A batch can include at most ${this.maxEmployees} employees`);
            return;
        }
        this.employees.update(list => [...list, user]);
    }

    removeEmployee(id: string): void {
        this.employees.update(list => list.filter(item => item.id !== id));
    }

    clearEmployees(): void {
        this.employees.set([]);
    }

    selectLanguage(language: CvLanguage): void {
        this.language.set(language);
    }

    onDeadlineChange(date: Date | null): void {
        this.deadline.set(date);
    }

    reasonMissing(): boolean {
        return !this.reason.value.trim();
    }

    departmentLabel(department: DepartmentNode): string {
        return `${department.code} - ${department.name}`;
    }

    teamLabel(team: TeamResponse): string {
        return `${team.code} - ${team.name}`;
    }

    // ---------- Steps ----------

    goToPreview(): void {
        this.submitAttempted.set(true);
        this.reason.markAsTouched();
        const criteria = this.buildCriteria();
        if (!criteria) {
            return;
        }
        this.criteria.set(criteria);
        this.preview.set(null);
        this.includedPage.set(0);
        this.excludedPage.set(0);
        this.step.set(BatchWizardStep.Preview);
        this.loadPreview();
    }

    onIncludedPage(event: PageEvent): void {
        this.includedPage.set(event.pageIndex);
        this.loadPreview();
    }

    onExcludedPage(event: PageEvent): void {
        this.excludedPage.set(event.pageIndex);
        this.loadPreview();
    }

    goToConfirm(): void {
        if (!this.preview() || this.nothingToCreate() || this.previewLoading()) {
            return;
        }
        this.step.set(BatchWizardStep.Confirm);
    }

    backToCriteria(): void {
        this.step.set(BatchWizardStep.Criteria);
    }

    backToPreview(): void {
        if (!this.creating()) {
            this.step.set(BatchWizardStep.Preview);
        }
    }

    /*
     * The count shown on the preview goes along as the confirmation. A changed recipient list
     * comes back as a 422 (the interceptor shows it) and the preview reloads for review.
     */
    confirm(): void {
        const criteria = this.criteria();
        if (!criteria || this.creating() || this.nothingToCreate()) {
            return;
        }
        this.creating.set(true);

        this.batchService.create({ ...criteria, expectedCount: this.includedCount() }).subscribe({
            next: batch => {
                this.creating.set(false);
                this.toast.success(`Batch created - sending ${batch.totalCount} request(s)`);
                void this.router.navigate(['/' + AppRoute.BatchDetail, batch.id]);
            },
            error: (error: HttpErrorResponse) => {
                this.creating.set(false);
                if (this.needsNewPreview(error)) {
                    this.step.set(BatchWizardStep.Preview);
                    this.loadPreview();
                }
            }
        })
    }

    cancel(): void {
        void this.router.navigate(['/' + AppRoute.UpdateRequests]);
    }

    @HostListener('document:click')
    closePickers(): void {
        if (this.openPicker()) {
            this.openPicker.set(null);
        }
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.openPicker.set(null);
    }

    // ---------- Private helpers ----------

    private buildCriteria(): BatchPreviewRequest | null {
        const targetIds = this.targetIds();
        const language = this.language();
        const deadline = toIsoDate(this.deadline());
        if (!targetIds.length || !language || !deadline || this.reason.invalid || this.reasonMissing()) {
            return null;
        }
        return {
            targetType: this.targetType(),
            targetIds,
            language,
            deadline,
            reason: this.reason.value.trim(),
        };
    }

    private loadPreview(): void {
        const criteria = this.criteria();
        if (!criteria) {
            return;
        }
        const seq = ++this.previewSeq;
        this.previewLoading.set(true);

        this.batchService.preview(criteria, {
            includedPage: this.includedPage(),
            excludedPage: this.excludedPage(),
            size: this.pageSize,
        }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: result => {
                if (seq !== this.previewSeq) {
                    return;
                }
                this.preview.set(result);
                this.previewLoading.set(false);
            },
            // A rejected criterion (inactive pick, past deadline...) is fixed on step 1
            error: () => {
                if (seq !== this.previewSeq) {
                    return;
                }
                this.previewLoading.set(false);
                if (!this.preview()) {
                    this.step.set(BatchWizardStep.Criteria);
                }
            },
        });
    }

    private needsNewPreview(error: HttpErrorResponse): boolean {
        const code = (error.error as ApiErrorResponse | null)?.code;
        return code === BatchErrorCode.PreviewOutdated || code === BatchErrorCode.Empty;
    }

    private loadDepartments(): void {
        this.departmentService.search({
            page: 0,
            size: BatchCreateComponent.OPTION_LOOKUP_SIZE,
        }).pipe(
            catchError(() => EMPTY),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => this.departments.set(page.content));
    }

    private loadTeams(): void {
        this.teamService.search({
            page: 0,
            size: BatchCreateComponent.OPTION_LOOKUP_SIZE,
        }).pipe(
            catchError(() => EMPTY),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => this.teams.set(page.content));
    }

    private watchKeyword(): void {
        this.keyword.valueChanges.pipe(
            startWith(this.keyword.value),
            debounceTime(BatchCreateComponent.SEARCH_DEBOUNCE_MS),
            distinctUntilChanged(),
            switchMap(keyword => this.lookupEmployees(keyword)),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(users => this.candidates.set(users));
    }

    // Active accounts only: the server refuses an inactive pick anyway
    private lookupEmployees(keyword: string): Observable<UserResponse[]> {
        // defer: the flag flips when the search starts, after switchMap dropped the previous one
        return defer(() => {
            this.searching.set(true);
            return this.userService.search({
                keyword: keyword.trim() || undefined,
                status: AccountStatus.Active,
                page: 0,
                size: BatchCreateComponent.EMPLOYEE_LOOKUP_SIZE,
            });
        }).pipe(
            map(page => page.content),
            catchError(() => of([] as UserResponse[])),
            finalize(() => this.searching.set(false)),
        );
    }
}