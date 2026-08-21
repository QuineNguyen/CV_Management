import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, output } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { DepartmentDialogState, FlatDepartment } from "../../../models/department.model";
import { DepartmentRequest } from "../../../dtos/department.dto";
import { DepartmentDialogMode } from "../../../enums/department-dialog-mode.enum";

@Component({
    selector: 'app-department-form-dialog',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './department-form-dialog.component.html',
    styleUrl: './department-form-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DepartmentFormDialogComponent implements OnInit {

    private readonly fb = inject(FormBuilder);

    readonly state = input.required<DepartmentDialogState>();
    readonly allDepartments = input.required<FlatDepartment[]>();
    readonly serverError = input<string | null>(null);
    readonly submitting = input(false);

    readonly submitted = output<DepartmentRequest>();
    readonly cancelled = output<void>();

    readonly form = this.fb.nonNullable.group({
        code: ['', [Validators.required, Validators.maxLength(50)]],
        name: ['', [Validators.required, Validators.maxLength(255)]],
        parentDepartmentId: [''],
    });

    readonly isEdit = computed(() => this.state().mode === DepartmentDialogMode.Edit);

    readonly title = computed(() => (this.isEdit() ? 'Edit department' : 'Add department'));

    // Own id and every descendant are removed so the UI cannot build a loop
    readonly parentOptions = computed<FlatDepartment[]>(() => {
        const editingId = this.state().department?.id;
        if (!editingId) {
            return this.allDepartments();
        }
        const blocked = this.collectDescendantIds(editingId);
        return this.allDepartments().filter(item => !blocked.has(item.id));
    })

    ngOnInit(): void {
        const { department, parentId } = this.state();
        this.form.patchValue({
            code: department?.code ?? '',
            name: department?.name ?? '',
            parentDepartmentId: department?.parentDepartmentId ?? parentId ?? '',
        });
    }
    
    // Used in options to visually indent and prefix the line leading to the parent.
    indent(depth: number): string {
        return depth === 0 ? '' : `${'\u00A0\u00A0'.repeat(depth)}└`;
    }

    hasError(control: 'code' | 'name', error: string): boolean {
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
            parentDepartmentId: raw.parentDepartmentId || null, // empty option means root
        })
    }

    onCancel(): void {
        this.cancelled.emit();
    }


    // Descendants are contiguous rows with a deeper depth in the flattened list.
    private collectDescendantIds(rootId: string): Set<string> {
        const list = this.allDepartments();
        const startIndex = list.findIndex(item => item.id === rootId);
        const blocked = new Set<string>();

        if (startIndex < 0) {
            return blocked;
        }

        const rootDepth = list[startIndex].depth;
        blocked.add(rootId);

        for (let i = startIndex + 1; i < list.length && list[i].depth > rootDepth; i++) {
            blocked.add(list[i].id);
        }
        return blocked;
    }
}