import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { CvProfileService } from "../../../services/cv-profile.service";
import { CvProfileDialogState } from "../../../models/cv-profile.model";
import { CvProfileRequest } from "../../../dtos/cv-profile.dto";
import { EmployeeTeamResponse } from "../../../dtos/employee-team.dto";
import { DialogMode } from "../../../enums/dialog-mode.enum";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

@Component({
    selector: 'app-profile-form-dialog',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './cv-profile-form-dialog.component.html',
    styleUrl: './cv-profile-form-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CVProfileFormDialogComponent implements OnInit {

    private readonly fb = inject(FormBuilder);
    private readonly profileService = inject(CvProfileService);
    private readonly destroyRef = inject(DestroyRef);

    readonly state = input.required<CvProfileDialogState>();
    readonly submitting = input(false);

    readonly employeeName = input<string | null>(null);

    readonly submitted = output<CvProfileRequest>();
    readonly cancelled = output<void>();

    readonly isClosing = signal(false);

    readonly form = this.fb.nonNullable.group({
        name: ['', [Validators.required, Validators.maxLength(255)]],
        description: ['', [Validators.maxLength(1000)]],
    });

    // Only teams the employee belongs to; the backend rejects anything else.
    readonly teamOptions = signal<EmployeeTeamResponse[]>([]);
    readonly teamsLoading = signal(false);
    readonly selectedTeamId = signal<string | null>(null);
    readonly teamPickerOpen = signal(false);
    readonly teamTouched = signal(false);

    readonly isEdit = computed(() => this.state().mode === DialogMode.Edit);

    readonly title = computed(() => (this.isEdit() ? 'Edit profile' : 'Add profile'));

    readonly selectedTeam = computed(() => 
        this.teamOptions().find(team => team.id === this.selectedTeamId()) ?? null);

    readonly teamMissing = computed(() => this.teamTouched() && !this.selectedTeamId());

    ngOnInit(): void {
        const { profile, employeeId } = this.state();

        this.form.patchValue({
            name: profile?.name ?? '',
            description: profile?.description ?? '',
        });
        this.selectedTeamId.set(profile?.linkedTeamId ?? null);

        this.loadTeams(employeeId);
    }

    toggleTeamPicker(event?: MouseEvent): void {
        event?.stopPropagation();
        this.teamPickerOpen.update(open => !open);
    }

    selectTeam(team: EmployeeTeamResponse): void {
        this.selectedTeamId.set(team.id);
        this.teamTouched.set(true);
        this.teamPickerOpen.set(false);
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        this.teamPickerOpen.set(false);
    }

    hasError(control: 'name' | 'description', error: string): boolean {
        const field = this.form.controls[control];
        return field.touched && field.hasError(error);
    }

    onSubmit(): void {
        this.teamTouched.set(true);

        if (this.form.invalid || !this.selectedTeamId() || this.submitting()) {
            this.form.markAllAsTouched();
            return;
        }

        const raw = this.form.getRawValue();
        this.submitted.emit({
            name: raw.name.trim(),
            description: raw.description.trim() || null,
            linkedTeamId: this.selectedTeamId()!, // Ensure not null
        });
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.teamPickerOpen()) {
            this.teamPickerOpen.set(false);
            return;
        }
        this.onCancel();
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

    private loadTeams(employeeId: string): void {
        this.teamsLoading.set(true);

        this.profileService.listAssignableTeams(employeeId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: teams => {
                    this.teamOptions.set(teams);
                    this.teamsLoading.set(false);

                    // Creating a profile: the primary team is the sensible default.
                    if (!this.selectedTeamId()) {
                        this.selectedTeamId.set(teams.find(team => team.primaryTeam)?.id ?? null);
                    }
                },
                error: () => this.teamsLoading.set(false),
            });
    }
}