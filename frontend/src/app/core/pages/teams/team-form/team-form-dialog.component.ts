import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { UserService } from "../../../services/user.service";
import { TeamDialogState } from "../../../models/team.model";
import { TeamRequest } from "../../../dtos/team.dto";
import { TechLeadOption } from "../../../dtos/user.dto";
import { DialogMode } from "../../../enums/dialog-mode.enum";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

@Component({
    selector: 'app-team-form-dialog',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './team-form-dialog.component.html',
    styleUrl: './team-form-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamFormDialogComponent implements OnInit {

    private readonly fb = inject(FormBuilder);
    private readonly userService = inject(UserService);
    private readonly destroyRef = inject(DestroyRef);

    readonly state = input.required<TeamDialogState>();
    readonly submitting = input(false);

    readonly submitted = output<TeamRequest>();
    readonly cancelled = output<void>();

    readonly form = this.fb.nonNullable.group({
        code: ['', [Validators.required, Validators.maxLength(50)]],
        name: ['', [Validators.required, Validators.maxLength(200)]],
        description: ['', [Validators.maxLength(2000)]],
        // Only active TECH_LEAD users are offered; the server rejects anyone else
        techLeadId: ['', [Validators.required]],
    });

    readonly techLeads = signal<TechLeadOption[]>([]);
    readonly techLeadOpen = signal(false);
    private readonly selectedTechLeadId = signal('');

    readonly selectedTechLeadLabel = computed(() => {
        const id = this.selectedTechLeadId();
        if (!id) {
            return 'Select a tech lead';
        }
        const lead = this.techLeads().find(l => l.id === id);
        return lead ? `${lead.fullName} (${lead.username})` : 'Select a tech lead';
    });

    readonly isEdit = computed(() => this.state().mode === DialogMode.Edit);
    readonly title = computed(() => (this.isEdit() ? 'Edit team' : 'Add team'));

    readonly canSubmit = computed(() => !this.submitting());

    ngOnInit(): void {
        const team = this.state().team;

        this.form.patchValue({
            code: team?.code ?? '',
            name: team?.name ?? '',
            description: team?.description ?? '',
            techLeadId: team?.techLeadId ?? '',
        });
        this.selectedTechLeadId.set(team?.techLeadId ?? '');

        this.loadTechLeads();
    }

    toggleTechLeadDropdown(event: MouseEvent): void {
        event.stopPropagation();
        this.techLeadOpen.update(open => !open);
    }

    selectTechLead(id: string): void {
        this.form.controls.techLeadId.setValue(id);
        this.form.controls.techLeadId.markAsDirty();
        this.selectedTechLeadId.set(id);
        this.techLeadOpen.set(false);
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        this.techLeadOpen.set(false);
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.techLeadOpen.set(false);
    }

    hasError(control: 'code' | 'name' | 'techLeadId', error: string): boolean {
        const field = this.form.controls[control];
        return field.touched && field.hasError(error);
    }

    onSubmit(): void {
        if (this.form.invalid || this.submitting()) {
            this.form.markAllAsTouched();
            return;
        }

        const raw = this.form.getRawValue();
        this.submitted.emit({
            code: raw.code.trim(),
            name: raw.name.trim(),
            description: raw.description.trim() || null,
            techLeadId: raw.techLeadId,
        });
    }

    onCancel(): void {
        this.cancelled.emit();
    }

    // ---------- Internals ----------

    private loadTechLeads(): void {
        this.userService.getTechLeads()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(leads => this.techLeads.set(leads));
    }
}