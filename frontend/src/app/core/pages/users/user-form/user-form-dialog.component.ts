import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from "@angular/forms";
import { DepartmentService } from "../../../services/department.service";
import { TeamService } from "../../../services/team.service";
import { UserDialogState } from "../../../models/user-page.model";
import { CreateUserRequest, TeamAssignment, UpdateUserRequest } from "../../../dtos/user.dto";
import { ROLE_LABELS } from "../../../models/user.model";
import { UserRole } from "../../../enums/user-role.enum";
import { DepartmentNode } from "../../../dtos/department.dto";
import { TeamResponse } from "../../../dtos/team.dto";
import { DialogMode } from "../../../enums/dialog-mode.enum";
import { catchError, debounceTime, distinctUntilChanged, EMPTY, forkJoin, map, merge, Subject, switchMap, tap } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

@Component({
    selector: 'app-user-form-dialog',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './user-form-dialog.component.html',
    styleUrl: './user-form-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormDialogComponent implements OnInit {

    private static readonly LOOKUP_SIZE = 20;
    private static readonly SEARCH_DEBOUNCE_MS = 250;

    private readonly fb = inject(FormBuilder);
    private readonly departmentService = inject(DepartmentService);
    private readonly teamService = inject(TeamService);
    private readonly destroyRef = inject(DestroyRef);

    readonly state = input.required<UserDialogState>();
    readonly submitting = input(false);

    readonly created = output<CreateUserRequest>();
    readonly updated = output<UpdateUserRequest>();
    readonly cancelled = output<void>();

    readonly isClosing = signal(false);

    readonly roleLabels = ROLE_LABELS;
    readonly roleOptions = Object.values(UserRole);
    readonly roleOpen = signal(false);
    private readonly selectedRole = signal<UserRole | ''>('');

    readonly selectedRoleLabel = computed(() => {
        const role = this.selectedRole();
        return role ? this.roleLabels[role] : 'Select a role';
    });

    readonly form = this.fb.nonNullable.group({
        fullName: ['', [Validators.required, Validators.maxLength(200)]],
        email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
        username: ['', [
            Validators.required,
            Validators.minLength(3),
            Validators.maxLength(100),
            Validators.pattern(/^[a-zA-Z0-9._-]+$/),
        ]],
        role: ['' as UserRole | '', [Validators.required]],
        dateOfBirth: [''],
        phoneNumber: ['', [Validators.maxLength(30)]],
        address: ['', [Validators.maxLength(500)]],
    });

    // Department picker
    readonly departmentKeyword = new FormControl('', { nonNullable: true });
    readonly selectedDepartment = signal<DepartmentNode | null>(null);
    readonly pickerOpen = signal(false);
    readonly departmentOptions = signal<DepartmentNode[]>([]);
    readonly lookupLoading = signal(false);

    // Team assignments
    readonly teamKeyword = new FormControl('', { nonNullable: true });
    readonly teamOptions = signal<TeamResponse[]>([]);
    readonly teamLookupLoading = signal(false);
    readonly teamPanelOpen = signal(false);
    readonly assignedTeams = signal<TeamResponse[]>([]);
    readonly primaryTeamId = signal<string | null>(null);

    // Assigned teams are dropped from the page after it arrives, so option counts cannot
    // be compared against the total — the server itself has to say the page was truncated
    readonly hasMoreTeamMatches = signal(false);

    readonly isEdit = computed(() => this.state().mode === DialogMode.Edit);
    readonly title = computed(() => (this.isEdit() ? 'Edit user' : 'Add user'));

    // Exactly one primary team is required and at least one team overall
    readonly canSubmit = computed(() =>
        !this.submitting()
        && this.selectedDepartment() !== null
        && this.assignedTeams().length > 0
        && this.primaryTeamId() !== null
    );

    private readonly reloadDepartment$ = new Subject<void>();
    private readonly reloadTeam$ = new Subject<void>();
    private departmentLookupStarted = false;

    ngOnInit(): void {
        const user = this.state().user;

        this.form.patchValue({
            fullName: user?.fullName ?? '',
            email: user?.email ?? '',
            username: user?.username ?? '',
            role: user?.role ?? '',
            dateOfBirth: user?.dateOfBirth ?? '',
            phoneNumber: user?.phoneNumber ?? '',
            address: user?.address ?? '',
        });
        this.selectedRole.set(user?.role ?? '');

        // Email and username cannot change after creation
        if (this.isEdit()) {
            this.form.controls.email.disable();
            this.form.controls.username.disable();
        }

        this.loadInitialDepartment(user?.primaryDepartmentId ?? null);
        this.loadInitialTeams();
        this.watchDepartmentKeyword();
        this.watchTeamKeyword();
    }

    // ---------- Role dropdown ----------
    toggleRoleDropdown(event: MouseEvent): void {
        event.stopPropagation();
        const next = !this.roleOpen();
        if (next) {
            this.pickerOpen.set(false);
        }
        this.roleOpen.set(next);
    }

    selectRole(role: UserRole | ''): void {
        this.form.controls.role.setValue(role);
        this.form.controls.role.markAsDirty();
        this.selectedRole.set(role);
        this.roleOpen.set(false);
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        this.roleOpen.set(false);
        this.teamPanelOpen.set(false);
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.roleOpen() || this.pickerOpen() || this.teamPanelOpen()) {
            this.roleOpen.set(false);
            this.pickerOpen.set(false);
            this.teamPanelOpen.set(false);
            return;
        }
        this.onCancel();
    }

    // ---------- Department picker ----------

    togglePicker(event?: MouseEvent): void {
        event?.stopPropagation();
        const opening = !this.pickerOpen();
        if (opening) {
            this.roleOpen.set(false);
            this.teamPanelOpen.set(false);
        }
        this.pickerOpen.set(opening);

        if (opening && !this.departmentLookupStarted) {
            this.departmentLookupStarted = true;
            this.reloadDepartment$.next();
        }
    }

    selectDepartment(department: DepartmentNode): void {
        this.selectedDepartment.set(department);
        this.pickerOpen.set(false);
    }

    // ---------- Team assignments ----------

    // Clicking or focusing the search field opens the panel on the full list, like the
    // department picker. Both events fire on a mouse click, so an already-open panel is a
    // no-op rather than a second lookup.
    openTeamPanel(event?: MouseEvent): void {
        event?.stopPropagation();
        if (this.teamPanelOpen()) {
            return;
        }

        this.roleOpen.set(false);
        this.pickerOpen.set(false);
        this.teamPanelOpen.set(true);
        // Reloaded on every open, since the assigned teams it has to exclude have changed
        this.reloadTeam$.next();
    }

    addTeam(team: TeamResponse): void {
        if (this.assignedTeams().some(assigned => assigned.id === team.id)) {
            return;
        }

        this.assignedTeams.update(teams => [...teams, team]);
        // The first team assigned becomes primary by default
        if (this.primaryTeamId() === null) {
            this.primaryTeamId.set(team.id);
        }

        // One pick closes the panel; the next one starts from the search field again
        this.teamKeyword.setValue('', { emitEvent: false });
        this.teamPanelOpen.set(false);
    }

    removeTeam(teamId: string): void {
        this.assignedTeams.update(teams => teams.filter(team => team.id !== teamId));

        // Removing the primary team leaves the choice to the admin
        if (this.primaryTeamId() === teamId) {
            this.primaryTeamId.set(this.assignedTeams()[0]?.id ?? null);
        }

    }

    setPrimary(teamId: string): void {
        this.primaryTeamId.set(teamId);
    }

    isPrimary(teamId: string): boolean {
        return this.primaryTeamId() === teamId;
    }

    // ---------- Submit ----------

    hasError(control: 'fullName' | 'email' | 'username' | 'role', error: string): boolean {
        const field = this.form.controls[control];
        return field.touched && field.hasError(error);
    }

    onSubmit(): void {
        const department = this.selectedDepartment();
        const primaryId = this.primaryTeamId();

        if (this.form.invalid || !department || !primaryId || this.submitting()) {
            this.form.markAllAsTouched();
            return;
        }

        const raw = this.form.getRawValue();
        const teams: TeamAssignment[] = this.assignedTeams().map(team => ({
            teamId: team.id,
            primary: team.id ===primaryId,
        }));

        const shared = {
            fullName: raw.fullName.trim(),
            role: raw.role as UserRole,
            primaryDepartmentId: department.id,
            dateOfBirth: raw.dateOfBirth || null,
            phoneNumber: raw.phoneNumber.trim() || null,
            address: raw.address.trim() || null,
            teams,
        };

        if (this.isEdit()) {
            this.updated.emit(shared);
            return;
        }

        this.created.emit({
            ...shared,
            email: raw.email.trim().toLowerCase(),
            username: raw.username.trim(),
        });
    }

    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget) {
            this.onCancel();
        }
    }

    onCancel(): void {
        if (this.isClosing() || this.submitting()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => {
            this.cancelled.emit();
        }, 500);
    }

    // ---------- Internals ----------

    private loadInitialDepartment(departmentId: string | null): void {
        if (!departmentId) {
            return;
        }

        this.departmentService.getById(departmentId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ next: department => this.selectedDepartment.set(department) });
    }

    // UserResponse carries only codes and names, so the full team rows are refetched
    private loadInitialTeams(): void {
        const memberships = this.state().user?.teams ?? [];
        if (!memberships.length) {
            return;
        }

        this.primaryTeamId.set(memberships.find(team => team.primary)?.teamId ?? null);

        forkJoin(memberships.map(membership => this.teamService.getById(membership.teamId)))
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(teams => {
                // Responses arrive in arbitrary order, so sort by code for a stable display
                this.assignedTeams.set([...teams].sort((a, b) => a.code.localeCompare(b.code)));
            });
    }

    private watchDepartmentKeyword(): void {
        merge(
            this.reloadDepartment$.pipe(map(() => this.departmentKeyword.value)),
            this.departmentKeyword.valueChanges.pipe(
                debounceTime(UserFormDialogComponent.SEARCH_DEBOUNCE_MS),
                distinctUntilChanged(),
            ),
        ).pipe(
            tap(() => this.lookupLoading.set(true)),
            switchMap(keyword => this.departmentService.search({
                keyword: keyword.trim() || undefined,
                page: 0,
                size: UserFormDialogComponent.LOOKUP_SIZE,
            }).pipe(
                catchError(() => {
                    this.lookupLoading.set(false);
                    return EMPTY;
                }),
            )),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => {
            this.departmentOptions.set(page.content);
            this.lookupLoading.set(false);
        });
    }

    private watchTeamKeyword(): void {
        merge(
            this.reloadTeam$.pipe(map(() => this.teamKeyword.value)),
            this.teamKeyword.valueChanges.pipe(
                debounceTime(UserFormDialogComponent.SEARCH_DEBOUNCE_MS),
                distinctUntilChanged(),
            ),
        ).pipe(
            tap(() => this.teamLookupLoading.set(true)),
            switchMap(keyword => this.teamService.search({
                keyword: keyword.trim() || undefined,
                page: 0,
                size: UserFormDialogComponent.LOOKUP_SIZE,
            }).pipe(
                catchError(() => {
                    this.teamLookupLoading.set(false);
                    return EMPTY;
                }),
            )),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => {
            // Teams already assigned are filtered out so the list only offers additions
            const assignedIds = new Set(this.assignedTeams().map(team => team.id));
            this.teamOptions.set(page.content.filter(team => !assignedIds.has(team.id)));
            this.hasMoreTeamMatches.set(page.totalElements > page.content.length);
            this.teamLookupLoading.set(false);
        });
    }
}