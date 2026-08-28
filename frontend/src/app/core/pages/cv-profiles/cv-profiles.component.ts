import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { DatePipe } from "@angular/common";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CvProfileService } from "../../services/cv-profile.service";
import { AuthService } from "../../services/auth.service";
import { ActivatedRoute } from "@angular/router";
import { ToastService } from "../../services/toast.service";
import { CvProfileRequest, CvProfileResponse } from "../../dtos/cv-profile.dto";
import { CvProfileDialogState, CvProfilePageState } from "../../models/cv-profile.model";
import { UserRole } from "../../enums/user-role.enum";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { QueryParam } from "../../enums/query-param.enum";
import { DialogMode } from "../../enums/dialog-mode.enum";
import { CVProfileFormDialogComponent } from "./cv-profile-form/cv-profile-form-dialog.component";
import { UserService } from "../../services/user.service";

@Component({
    selector: 'app-profiles',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, CVProfileFormDialogComponent, DatePipe],
    templateUrl: './cv-profiles.component.html',
    styleUrl: './cv-profiles.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CVProfilesComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;

    private readonly profileService = inject(CvProfileService);
    private readonly userService = inject(UserService);
    private readonly auth = inject(AuthService);
    private readonly route = inject(ActivatedRoute);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];

    // Whose profiles are on screen: the signed-in user or an employee an admin linked to.
    readonly employeeId = signal<string>('');
    readonly employeeName = signal<string | null>(null);

    readonly profiles = signal<CvProfileResponse[]>([]);
    readonly loading = signal(false);

    readonly pageState = signal<CvProfilePageState>({
        index: 0,
        size: CVProfilesComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly dialogState = signal<CvProfileDialogState | null>(null);
    readonly saving = signal(false);

    readonly deleteTarget = signal<CvProfileResponse | null>(null);
    readonly deleting = signal(false);
    readonly isDeleteClosing = signal(false);

    // Id of the profile currently being promoted, so only its button shows a pending state.
    readonly promotingId = signal<string | null>(null);

    // Create, rename and re-primary are the owner's own actions.
    readonly canEdit = computed(() => this.isOwnPage());
    
    // Deleting belongs to Admin and HR, on anyone's page - including their own.
    readonly canDelete = computed(() => this.auth.hasRole(UserRole.Admin, UserRole.HR));

    readonly isOwnPage = computed(() => this.auth.user()?.id === this.employeeId());

    ngOnInit(): void {
        this.route.queryParamMap
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(params => {
                const target = params.get(QueryParam.EmployeeId) ?? this.auth.user()?.id ?? '';
                this.employeeId.set(target);
                this.resolveEmployeeName(target);
                this.pageState.update(state => ({ ...state, index: 0 }));
                this.load(true);
            });
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        const employeeId = this.employeeId();
        if (!employeeId) {
            return;
        }
        if (showSpinner) {
            this.loading.set(true);
        }

        const { index, size } = this.pageState();

        this.profileService.listByEmployee({ employeeId, page: index, size }).subscribe({
            next: result => {
                this.profiles.set(result.content);
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

    // ---------- Dialog ----------

    openCreate(): void {
        this.dialogState.set({
            mode: DialogMode.Create,
            profile: null,
            employeeId: this.employeeId(),
        });
    }

    openEdit(profile: CvProfileResponse): void {
        this.dialogState.set({
            mode: DialogMode.Edit,
            profile,
            employeeId: profile.employeeId,
        });
    }

    closeDialog(): void {
        this.dialogState.set(null);
        this.saving.set(false);
    }

    submitDialog(body: CvProfileRequest): void {
        const state = this.dialogState();
        if (!state) {
            return;
        }
        this.saving.set(true);
        const isEdit = state.mode === DialogMode.Edit && state.profile;

        const request$ = isEdit
            ? this.profileService.update(state.profile!.id, body)
            : this.profileService.create(state.employeeId, body);

        request$.subscribe({
            next: saved => {
                this.saving.set(false);
                this.closeDialog();
                this.toast.success(isEdit
                    ? `Profile "${saved.name}" updated`
                    : `Profile "${saved.name} created`
                );
                this.load(false);
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Primary ----------

    setPrimary(profile: CvProfileResponse): void {
        if (profile.primary || this.promotingId()) {
            return;
        }
        this.promotingId.set(profile.id);

        this.profileService.setPrimary(profile.id).subscribe({
            next: saved => {
                this.promotingId.set(null);
                this.toast.success(`"${saved.name}" is now the primary profile`);
                this.load(false);
            },
            error: () => this.promotingId.set(null),
        });
    }

    // ---------- Delete ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.deleteTarget()) {
            this.cancelDelete();
        }
    }

    askDelete(profile: CvProfileResponse): void {
        this.isDeleteClosing.set(false);
        this.deleteTarget.set(profile);
    }

    cancelDelete(): void {
        if (this.isDeleteClosing() || this.deleting()) {
            return;
        }
        this.isDeleteClosing.set(true);
        setTimeout(() => {
            this.deleteTarget.set(null);
            this.deleting.set(false);
            this.isDeleteClosing.set(false);
        }, 500);
    }

    confirmDelete(): void {
        const target = this.deleteTarget();
        if (!target) {
            return;
        }
        this.deleting.set(true);

        this.profileService.delete(target.id).subscribe({
            next: () => {
                this.deleteTarget.set(null);
                this.deleting.set(false);
                this.isDeleteClosing.set(false);
                this.toast.success(`Profile "${target.name}" deleted.`);
                this.load(false);
            },
            error: () => {
                this.deleting.set(false);
                this.deleteTarget.set(null);
                this.isDeleteClosing.set(false);
            },
        });
    }

    // ---------- Tooltips ----------

    primaryActionHint(profile: CvProfileResponse): string {
        if (profile.primary) {
            return 'Already the primary profile';
        }
        if (this.promotingId() === profile.id) {
            return 'Setting as primary...';
        }
        return 'Set as primary profile';
    }

    deleteActionHint(profile: CvProfileResponse): string {
        return profile.primary
            ? 'The primary profile cannot be deleted'
            : 'Delete';
    }

    // Own page reads the session; a deep link resolves the name so the dialog can name its target.
    private resolveEmployeeName(employeeId: string): void {
        const current = this.auth.user();

        if (!employeeId) {
            this.employeeName.set(null);
            return;
        }
        if (current?.id === employeeId) {
            this.employeeName.set(current.fullName);
            return;
        }

        this.employeeName.set(null);
        this.userService.getById(employeeId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ next: user => this.employeeName.set(user.fullName) });
    }
}