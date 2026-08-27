import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { UserService } from "../../services/user.service";
import { DepartmentService } from "../../services/department.service";
import { AuthService } from "../../services/auth.service";
import { ToastService } from "../../services/toast.service";
import { ROLE_LABELS } from "../../models/user.model";
import { UserRole } from "../../enums/user-role.enum";
import { AccountStatus } from "../../enums/account-status.enum";
import { AvatarView, TemporaryPasswordState, UserDialogState, UserPageState } from "../../models/user-page.model";
import { CreateUserRequest, DeactivateUserRequest, UpdateUserRequest, UserResponse } from "../../dtos/user.dto";
import { DepartmentNode } from "../../dtos/department.dto";
import { DialogMode } from "../../enums/dialog-mode.enum";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { catchError, debounceTime, distinctUntilChanged, EMPTY } from "rxjs";
import { UserFormDialogComponent } from "./user-form/user-form-dialog.component";
import { UserDeactivateDialogComponent } from "./user-deactivate/user-deactivate-dialog.component";
import { AriaSortDirection, SortDirection, SortIcon, UserSortField } from "../../enums/sort-field.enum";
import { SortState } from "../../models/sort-state.model";

@Component({
    selector: 'app-users',
    standalone: true,
    imports: [
        ReactiveFormsModule,
        MatPaginatorModule,
        MatTooltipModule,
        UserFormDialogComponent,
        UserDeactivateDialogComponent,
    ],
    templateUrl: './users.component.html',
    styleUrl: './users.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 20;
    private static readonly SEARCH_DEBOUNCE_MS = 300;
    private static readonly DEPARTMENT_LOOKUP_SIZE = 100;
    private static readonly AVATAR_TONES = 6;

    private readonly userService = inject(UserService);
    private readonly departmentService = inject(DepartmentService);
    private readonly auth = inject(AuthService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly roleLabels = ROLE_LABELS;
    readonly roleOptions = Object.values(UserRole);
    readonly statusOptions = Object.values(AccountStatus);

    readonly pageState = signal<UserPageState>({
        index: 0,
        size: UsersComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly users = signal<UserResponse[]>([]);
    readonly departments = signal<DepartmentNode[]>([]);
    readonly loading = signal(false);

    readonly keyword = new FormControl('', { nonNullable: true });
    readonly roleFilter = signal<UserRole | null>(null);
    readonly roleOpen = signal(false);
    readonly statusFilter = signal<AccountStatus | null>(null);
    readonly statusOpen = signal(false);
    readonly departmentFilter = signal<string | null>(null);
    readonly departmentOpen = signal(false);

    readonly selectedRoleLabel = computed(() => {
        const role = this.roleFilter();
        return role ? this.roleLabels[role] : 'All roles';
    });

    readonly selectedStatusLabel = computed(() => {
        const status = this.statusFilter();
        return status ? status : 'All statuses';
    });

    readonly selectedDepartmentLabel = computed(() => {
        const id = this.departmentFilter();
        if (!id) {
            return 'All departments';
        }
        const dept = this.departments().find(d => d.id === id);
        return dept ? `${dept.code} — ${dept.name}` : 'All departments';
    });

    readonly dialogState = signal<UserDialogState | null>(null);
    readonly saving = signal(false);

    readonly deactivateTarget = signal<UserResponse | null>(null);
    readonly deactivating = signal(false);

    readonly activateTarget = signal<UserResponse | null>(null);
    readonly activating = signal(false);

    readonly resetTarget = signal<UserResponse | null>(null);
    readonly resetting = signal(false);

    readonly temporaryPassword = signal<TemporaryPasswordState | null>(null);

    // HR reaches this page read-only; every write action is hidden for them
    readonly canManage = computed(() => this.auth.hasRole(UserRole.Admin));

    // Tech leads see a narrow set, so the empty state must not read like a missing record
    readonly isScopedView = computed(() => this.auth.hasRole(UserRole.TechLead));

    // The server blocks self-deactivation, so the action is hidden up front
    readonly currentUserId = computed(() => this.auth.user()?.id ?? null);

    readonly isEmpty = computed(() => !this.loading() && this.users().length === 0);

    readonly activeStatus = AccountStatus.Active;

    readonly sortField = UserSortField;

    readonly sort = signal<SortState<UserSortField>>({
        field: UserSortField.FullName,
        direction: SortDirection.Asc,
    });

    ngOnInit(): void {
        this.loadDepartments();
        this.watchKeyword();
        this.loadUsers(true);
    }

    // ---------- Loading ----------

    loadUsers(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }

        const { index, size } = this.pageState();
        const activeSort = this.sort();

        this.userService.search({
            keyword: this.keyword.value.trim() || undefined,
            role: this.roleFilter() ?? undefined,
            status: this.statusFilter() ?? undefined,
            departmentId: this.departmentFilter() ?? undefined,
            sortBy: activeSort.field,
            direction: activeSort.direction,
            page: index,
            size,
        }).subscribe({
            next: result => {
                this.users.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.loadUsers(true);
    }

    toggleRoleDropdown(event: MouseEvent): void {
        event.stopPropagation();
        const next = !this.roleOpen();
        this.closeDropdowns();
        this.roleOpen.set(next);
    }

    selectRole(role: UserRole | null): void {
        this.roleFilter.set(role);
        this.roleOpen.set(false);
        this.resetToFirstPage();
    }

    toggleStatusDropdown(event: MouseEvent): void {
        event.stopPropagation();
        const next = !this.statusOpen();
        this.closeDropdowns();
        this.statusOpen.set(next);
    }

    selectStatus(status: AccountStatus | null): void {
        this.statusFilter.set(status);
        this.statusOpen.set(false);
        this.resetToFirstPage();
    }

    toggleDepartmentDropdown(event: MouseEvent): void {
        event.stopPropagation();
        const next = !this.departmentOpen();
        this.closeDropdowns();
        this.departmentOpen.set(next);
    }

    selectDepartment(id: string | null): void {
        this.departmentFilter.set(id);
        this.departmentOpen.set(false);
        this.resetToFirstPage();
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        if (!this.roleOpen() && !this.statusOpen() && !this.departmentOpen()) {
            return;
        }
        this.roleOpen.set(false);
        this.statusOpen.set(false);
        this.departmentOpen.set(false);
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.closeDropdowns();
    }

    clearFilters(): void {
        this.keyword.setValue('', { emitEvent: false });
        this.sort.set({ field: UserSortField.FullName, direction: SortDirection.Asc });
        this.roleFilter.set(null);
        this.statusFilter.set(null);
        this.departmentFilter.set(null);
        this.closeDropdowns();
        this.resetToFirstPage();
    }

    // Column Name has only 2 states (Asc <-> Desc). Other columns cycle Asc -> Desc -> default (FullName Asc)
    toggleSort(field: UserSortField): void {
        this.sort.update(current => {
            if (field === UserSortField.FullName) {
                if (current.field === UserSortField.FullName) {
                    return {
                        field,
                        direction: current.direction === SortDirection.Asc
                            ? SortDirection.Desc
                            : SortDirection.Asc,
                    };
                }
                return { field, direction: SortDirection.Asc };
            }

            if (current.field !== field) {
                return { field, direction: SortDirection.Asc };
            }
            return current.direction === SortDirection.Asc
                ? { field, direction: SortDirection.Desc }
                : { field: UserSortField.FullName, direction: SortDirection.Asc };
        });
        this.resetToFirstPage();
    }

    // ARIA value for a sortable header cell
    ariaSort(field: UserSortField): AriaSortDirection.Ascending | AriaSortDirection.Descending | AriaSortDirection.None {
        const current = this.sort();
        if (current.field !== field) {
            return AriaSortDirection.None;
        }
        return current.direction === SortDirection.Asc ? AriaSortDirection.Ascending : AriaSortDirection.Descending;
    }

    sortIcon(field: UserSortField): SortIcon {
        const current = this.sort();
        if (current.field !== field) {
            return SortIcon.None;
        }
        return current.direction === SortDirection.Asc ? SortIcon.Up : SortIcon.Down;
    }

    // ---------- Create / edit dialog ----------

    openCreate(): void {
        this.dialogState.set({ mode: DialogMode.Create, user: null });
    }

    openEdit(user: UserResponse): void {
        this.dialogState.set({ mode: DialogMode.Edit, user });
    }

    closeDialog(): void {
        this.dialogState.set(null);
        this.saving.set(false);
    }

    submitCreate(body: CreateUserRequest): void {
        this.saving.set(true);
        this.userService.create(body).subscribe({
            next: created => {
                this.saving.set(false);
                this.closeDialog();
                // The password is shown only once and never returned again
                this.temporaryPassword.set({ 
                    title: 'Account created',
                    fullName: created.user.fullName,
                    password: created.temporaryPassword,
                    copied: false,
                });
                this.loadUsers(false);
            },
            error: () => this.saving.set(false),
        });
    }

    submitUpdate(body: UpdateUserRequest): void {
        const target = this.dialogState()?.user;
        if (!target) {
            return;
        }

        this.saving.set(true);
        this.userService.update(target.id, body).subscribe({
            next: saved => {
                this.saving.set(false);
                this.closeDialog();
                this.toast.success(`User ${saved.username} updated`);
                this.loadUsers(false);
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Temporary password ----------

    async copyPassword(): Promise<void> {
        const state = this.temporaryPassword();
        if (!state) {
            return;
        }

        try {
            await navigator.clipboard.writeText(state.password);
            this.temporaryPassword.set({ ...state, copied: true });
        } catch {
            // Clipboard access can be denied; the value stays visible for manual copying
            this.toast.error('Could not copy. Select the password and copy it manually');
        }
    }

    dismissPassword(): void {
        this.temporaryPassword.set(null);
    }

    // ---------- Deactivate ----------
    askDeactivate(user: UserResponse): void {
        // ledTeams only arrives on the detail endpoint, so the row is refetched first
        this.userService.getById(user.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: detail => this.deactivateTarget.set(detail),
                error: () => this.deactivateTarget.set(null),
            });
    }

    cancelDeactivate(): void {
        this.deactivateTarget.set(null);
        this.deactivating.set(false);
    }

    confirmDeactivate(body: DeactivateUserRequest): void {
        const target = this.deactivateTarget();
        if (!target) {
            return;
        }

        this.deactivating.set(true);
        this.userService.deactivate(target.id, body).subscribe({
            next: () => {
                this.cancelDeactivate();
                this.toast.success(`User ${target.username} deactivated`);
                this.loadUsers(false);
            },
            error: () => this.deactivating.set(false),
        });
    }

    // ---------- Activate ----------
    askActivate(user: UserResponse): void {
        this.activateTarget.set(user);
    }

    cancelActivate(): void {
        this.activateTarget.set(null);
        this.activating.set(false);
    }

    confirmActivate(): void {
        const target = this.activateTarget();
        if (!target) {
            return;
        }

        this.activating.set(true);
        this.userService.activate(target.id).subscribe({
            next: () => {
                this.cancelActivate();
                this.toast.success(`User ${target.username} reactivated`);
                this.loadUsers(false);
            },
            error: () => {
                this.activating.set(false);
                this.activateTarget.set(null);
            },
        });
    }

    // ---------- Reset password ----------

    askResetPassword(user: UserResponse): void {
        this.resetTarget.set(user);
    }

    cancelReset(): void {
        this.resetTarget.set(null);
        this.resetting.set(false);
    }

    confirmReset(): void {
        const target = this.resetTarget();
        if (!target) {
            return;
        }

        this.resetting.set(true);
        this.auth.resetPassword(target.id).subscribe({
            next: result => {
                this.cancelReset();
                // The password is showwn once and never returned again
                this.temporaryPassword.set({
                    title: 'Password reset',
                    fullName: target.fullName,
                    password: result.temporaryPassword,
                    copied: false,
                });
                this.loadUsers(false);
            },
            error: () => this.resetting.set(false),
        });
    }

    // ---------- Avatar (initially setup colour) ----------
    
    // Initials plus a stable colour tone, so the same person always looks the same.
    avatarOf(user: UserResponse): AvatarView {
        const parts = user.fullName.trim().split(/\s+/);
        const initials = parts.length === 1
            ? parts[0].slice(0, 2)
            : parts[0][0] + parts[parts.length - 1][0];

        // Hash the id rather than the name, so a rename does not change the colour
        let hash = 0;
        for (const char of user.id) {
            hash = (hash * 31 + char.charCodeAt(0)) % 997;
        }

        return {
            initials: initials.toLowerCase(),
            tone: hash % UsersComponent.AVATAR_TONES,
        };
    }

    selfActionHint(userId: string, action: string): string {
        return userId === this.currentUserId()
            ? `You cannot ${action} your own account`
            : action.charAt(0).toUpperCase() + action.slice(1);
    }

    // ---------- Internals ----------

    private watchKeyword(): void {
        this.keyword.valueChanges.pipe(
            debounceTime(UsersComponent.SEARCH_DEBOUNCE_MS),
            distinctUntilChanged(),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(() => this.resetToFirstPage());
    }

    private loadDepartments(): void {
        this.departmentService.search({
            page: 0,
            size: UsersComponent.DEPARTMENT_LOOKUP_SIZE,
        }).pipe(
            catchError(() => EMPTY),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => this.departments.set(page.content));
    }

    private resetToFirstPage(): void {
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.loadUsers(true);
    }
}