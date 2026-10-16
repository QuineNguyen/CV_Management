import { ChangeDetectionStrategy, Component, computed, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { SkillDialogState } from "../../../../models/skill-page.model";
import { SkillRequest } from "../../../../dtos/skill.dto";
import { toSignal } from "@angular/core/rxjs-interop";
import { DialogMode } from "../../../../enums/dialog-mode.enum";

/*
 * Add / edit one catalogue skill. Rendered inline by the page, not via MatDialog.
 * - Code is UPPER_SNAKE_CASE; typing is upper-cased and spaces become underscores as you go.
 * - CVs store the skill id, not its name, so a rename changes every published version and every
 * PDF exported later. Saving a new name therefore asks for an explicit confirmation first.
 */
@Component({
    selector: 'app-skill-form-dialog',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './skill-form-dialog.component.html',
    styleUrl: './skill-form-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkillFormDialogComponent implements OnInit {

    private static readonly CLOSE_DELAY_MS = 500;
    private static readonly CODE_MAX_LENGTH = 50;
    private static readonly NAME_MAX_LENGTH = 255;
    private static readonly DISPLAY_ORDER_MAX = 1_000_000;
    private static readonly CODE_PATTERN = /^[A-Z0-9]+(?:_[A-Z0-9]+)*$/;

    private readonly fb = inject(NonNullableFormBuilder);

    readonly state = input.required<SkillDialogState>();
    readonly submitting = input(false);

    readonly saved = output<SkillRequest>();
    readonly cancelled = output<void>();
    
    readonly codeMaxLength = SkillFormDialogComponent.CODE_MAX_LENGTH;
    readonly nameMaxLength = SkillFormDialogComponent.NAME_MAX_LENGTH;
    readonly isClosing = signal(false);
    private backdropMouseDownTarget: EventTarget | null = null;

    // Body waiting for the rename confirmation; null when no confirmation is open
    readonly pendingRename = signal<SkillRequest | null>(null);
    private confirmMouseDownTarget: EventTarget | null = null;

    readonly form = this.fb.group({
        code: ['', [
            Validators.required,
            Validators.maxLength(SkillFormDialogComponent.CODE_MAX_LENGTH),
            Validators.pattern(SkillFormDialogComponent.CODE_PATTERN),
        ]],
        name: ['', [Validators.required, Validators.maxLength(SkillFormDialogComponent.NAME_MAX_LENGTH)]],
        displayOrder: [0, [
            Validators.required,
            Validators.min(0),
            Validators.max(SkillFormDialogComponent.DISPLAY_ORDER_MAX),
        ]],
    });

    private readonly nameValue = toSignal(this.form.controls.name.valueChanges, { initialValue: '' });

    readonly isEdit = computed(() => this.state().mode === DialogMode.Edit);
    readonly title = computed(() => (this.isEdit() ? 'Edit skill' : 'Add skill'));
    readonly usedBy = computed(() => this.state().skill?.cvCount ?? 0);
    readonly originalName = computed(() => this.state().skill?.name ?? '');

    // CVs whose display changes if this name is saved
    readonly renameImpact = computed(() => {
        const skill = this.state().skill;
        if (!skill || skill.cvCount === 0) {
            return 0;
        }
        return SkillFormDialogComponent.normaliseName(this.nameValue()) !== skill.name ? skill.cvCount : 0;
    });

    ngOnInit(): void {
        const skill = this.state().skill;
        if (skill) {
            this.form.setValue({ code: skill.code, name: skill.name, displayOrder: skill.displayOrder });
        }
    }

    // Keeps the code in UPPER_SNAKE_CASE while typing
    normaliseCode(): void {
        const control = this.form.controls.code;
        const next = control.value.toUpperCase().replace(/\s+/g, '_');
        if (next !== control.value) {
            control.setValue(next);
        }
    }

    hasError(controlName: 'code' | 'name' | 'displayOrder', error: string): boolean {
        const control = this.form.controls[controlName];
        return control.touched && control.hasError(error);
    }

    submit(): void {
        if (this.form.invalid) {
            this.form.markAllAsTouched();
            return;
        }
        const value = this.form.getRawValue();
        const body: SkillRequest = {
            code: value.code.trim(),
            name: SkillFormDialogComponent.normaliseName(value.name),
            displayOrder: Number(value.displayOrder),
        };

        // Any rename, used or not: older versions outside the usage count may still show it
        if (this.isEdit() && body.name !== this.originalName()) {
            this.pendingRename.set(body);
            return;
        }
        this.saved.emit(body);
    }

    // ---------- Rename confirmation ----------

    confirmRename(): void {
        const body = this.pendingRename();
        if (!body) {
            return;
        }
        this.pendingRename.set(null);
        this.saved.emit(body);
    }

    // Back to the form with the typed values kept
    keepEditing(): void {
        this.pendingRename.set(null);
    }

    onConfirmBackdropMouseDown(event: MouseEvent): void {
        this.confirmMouseDownTarget = event.target;
    }

    onConfirmBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.confirmMouseDownTarget === event.currentTarget) {
            this.keepEditing();
        }
        this.confirmMouseDownTarget = null;
    }

    // ---------- Closing ----------

    cancel(): void {
        if (this.isClosing() || this.submitting()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => this.cancelled.emit(), SkillFormDialogComponent.CLOSE_DELAY_MS);
    }

    onBackdropMouseDown(event: MouseEvent): void {
        this.backdropMouseDownTarget = event.target;
    }

    // Only a click that starts and ends on the backdrop closes it; a text drag does not
    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.backdropMouseDownTarget === event.currentTarget) {
            this.cancel();
        }
        this.backdropMouseDownTarget = null;
    }

    // Escape steps back one level: the confirmation first, then the dialog
    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.pendingRename()) {
            this.keepEditing();
            return;
        }
        this.cancel();
    }

    // Trimmed with inner whitespace collapsed, the same rule as the server
    private static normaliseName(name: string): string {
        return name.trim().replace(/\s+/g, ' ');
    }
}