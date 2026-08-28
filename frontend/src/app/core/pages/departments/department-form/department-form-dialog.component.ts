import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from "@angular/forms";
import { DepartmentDialogState } from "../../../models/department.model";
import { DepartmentNode, DepartmentRequest } from "../../../dtos/department.dto";
import { DialogMode } from "../../../enums/dialog-mode.enum";
import { DepartmentService } from "../../../services/department.service";
import { takeUntilDestroyed, toSignal } from "@angular/core/rxjs-interop";
import { catchError, debounceTime, distinctUntilChanged, EMPTY, map, merge, startWith, Subject, switchMap, tap } from "rxjs";

@Component({
    selector: 'app-department-form-dialog',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './department-form-dialog.component.html',
    styleUrl: './department-form-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DepartmentFormDialogComponent implements OnInit {

    // One page of suggestions is enough for a picker; typing narrows it further.
    private static readonly LOOKUP_SIZE = 20;

    private static readonly SEARCH_DEBOUNCE_MS = 250;
    
    private readonly fb = inject(FormBuilder);
    private readonly departmentService = inject(DepartmentService);
    private readonly destroyRef = inject(DestroyRef);

    readonly state = input.required<DepartmentDialogState>();
    readonly submitting = input(false);

    readonly submitted = output<DepartmentRequest>();
    readonly cancelled = output<void>();

    readonly isClosing = signal(false);

    readonly form = this.fb.nonNullable.group({
        code: ['', [Validators.required, Validators.maxLength(50)]],
        name: ['', [Validators.required, Validators.maxLength(255)]],
    });

    readonly parentKeyword = new FormControl('', { nonNullable: true });
    readonly selectedParent = signal<DepartmentNode | null>(null);
    readonly pickerOpen = signal(false);
    readonly parentOptions = signal<DepartmentNode[]>([]);
    readonly lookupLoading = signal(false);
    readonly lookupTotal = signal(0);

    readonly isEdit = computed(() => this.state().mode === DialogMode.Edit);

    readonly title = computed(() => (this.isEdit() ? 'Edit department' : 'Add department'));

    readonly hasMoreMatches = computed(() => this.lookupTotal() > this.parentOptions().length);

    // Fires the first lookup when the picker opens, so a closed picker costs nothing.
    private readonly reload$ = new Subject<void>();

    private lookupStarted = false;

    // Server includes the edited node and its descendants, so no loop can be picked.
    readonly suggestions = toSignal(
        this.parentKeyword.valueChanges.pipe(
            startWith(''),
            debounceTime(250),
            distinctUntilChanged(),
            switchMap(keyword => {
                this.lookupLoading.set(true);
                return this.departmentService.search({
                    keyword: keyword.trim() || undefined,
                    excludeSubtreeOf: this.state().department?.id,
                    page: 0,
                    size: DepartmentFormDialogComponent.LOOKUP_SIZE,
                });
            }),
            takeUntilDestroyed(),
        ),
        { initialValue: null },
    )

    ngOnInit(): void {
        const { department, parentId } = this.state();

        this.form.patchValue({
            code: department?.code ?? '',
            name: department?.name ?? '',
        });

        this.loadInitialParent(department?.parentDepartmentId ?? parentId);
        this.watchKeyword();
    }

    togglePicker(): void {
        const opening = !this.pickerOpen();
        this.pickerOpen.set(opening);

        if (opening && !this.lookupStarted) {
            this.lookupStarted = true;
            this.reload$.next();
        }
    }

    selectParent(parent: DepartmentNode | null): void {
        this.selectedParent.set(parent);
        this.pickerOpen.set(false);
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
            parentDepartmentId: this.selectedParent()?.id ?? null, // empty option means root
        })
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.pickerOpen()) {
            this.pickerOpen.set(false);
            return;
        }
        this.onCancel();
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

    private loadInitialParent(parentId: string | null): void {
        if (!parentId) {
            return;
        }

        this.departmentService.getById(parentId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ next: parent => this.selectedParent.set(parent) });
    }

    private watchKeyword(): void {
        const excludeSubtreeOf = this.state().department?.id;

        merge(
            // Manual trigger reuses whatever is already typed.
            this.reload$.pipe(map(() => this.parentKeyword.value)),
            this.parentKeyword.valueChanges.pipe(
                debounceTime(DepartmentFormDialogComponent.SEARCH_DEBOUNCE_MS),
                distinctUntilChanged(),
            ),
        ).pipe(
            tap(() => this.lookupLoading.set(true)),
            switchMap(keyword => this.departmentService.search({
                keyword: keyword.trim() || undefined,
                excludeSubtreeOf,
                page: 0,
                size: DepartmentFormDialogComponent.LOOKUP_SIZE,
            }).pipe(
                catchError(() => {
                    this.lookupLoading.set(false);
                    return EMPTY;
                }),
            )),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => {
            this.parentOptions.set(page.content);
            this.lookupTotal.set(page.totalElements);
            this.lookupLoading.set(false);
        })
    }
}