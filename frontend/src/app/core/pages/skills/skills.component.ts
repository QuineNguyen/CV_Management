import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from "@angular/core";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { SortState } from "../../models/sort-state.model";
import { AriaSortDirection, SkillSortField, SortDirection, SortIcon } from "../../enums/sort-field.enum";
import { SkillService } from "../../services/skill.service";
import { ToastService } from "../../services/toast.service";
import { SkillRequest, SkillResponse } from "../../dtos/skill.dto";
import { SkillDialogState, SkillPageState } from "../../models/skill-page.model";
import { debounceTime, map } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { DialogMode } from "../../enums/dialog-mode.enum";
import { SkillFormDialogComponent } from "./skill-form/skill-form-dialog.component";

/*
 * Skill catalogue management (Admin/HR).
 * - Add and edit only: the delete action does not exist. Unused skills are pushed down
 * by display order instead.
 * - "CVs using" counts the latest published version of active CVs - the scope of the CV search
 * skill filter, so both screens give the same number.
 */
@Component({
    selector: 'app-skills',
    standalone: true,
    imports: [ReactiveFormsModule, MatPaginatorModule, MatTooltipModule, DatePipe, SkillFormDialogComponent],
    templateUrl: './skills.component.html',
    styleUrl: './skills.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkillsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 20;
    private static readonly SEARCH_DEBOUNCE_MS = 300;
    // Catalogue order
    private static readonly DEFAULT_SORT: SortState<SkillSortField> = {
        field: SkillSortField.DisplayOrder,
        direction: SortDirection.Asc,
    };

    private readonly skillService = inject(SkillService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [10, 20, 50, 100];
    readonly sortField = SkillSortField;

    readonly skills = signal<SkillResponse[]>([]);
    readonly loading = signal(false);
    readonly pageState = signal<SkillPageState>({
        index: 0,
        size: SkillsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly keywordInput = new FormControl('', { nonNullable: true });
    readonly keyword = signal('');
    readonly sort = signal<SortState<SkillSortField>>(SkillsComponent.DEFAULT_SORT);

    readonly dialogState = signal<SkillDialogState | null>(null);
    readonly saving = signal(false);

    readonly isEmpty = computed(() => !this.loading() && this.skills().length === 0);
    readonly hasFilters = computed(() => this.keyword().length > 0);

    ngOnInit(): void {
        this.watchKeyword();
        this.load(true);
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();
        const activeSort = this.sort();

        this.skillService.search({
            keyword: this.keyword() || undefined,
            sortBy: activeSort.field,
            direction: activeSort.direction,
            page: index,
            size,
        }).subscribe({
            next: result => {
                this.skills.set(result.content);
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

    clearFilters(): void {
        this.keywordInput.setValue('', { emitEvent: false });
        this.keyword.set('');
        this.sort.set(SkillsComponent.DEFAULT_SORT);
        this.resetToFirstPage();
    }

    // ---------- Sorting ----------

    // Display order toggles Asc <-> Desc; other columns cycle Asc -> Desc -> catalogue order
    toggleSort(field: SkillSortField): void {
        this.sort.update(current => {
            if (field === SkillSortField.DisplayOrder) {
                return current.field === field && current.direction === SortDirection.Asc
                    ? { field, direction: SortDirection.Desc }
                    : { field, direction: SortDirection.Asc };
            }
            if (current.field !== field) {
                return { field, direction: SortDirection.Asc };
            }
            return current.direction === SortDirection.Asc
                ? { field, direction: SortDirection.Desc }
                : SkillsComponent.DEFAULT_SORT;
        });
        this.resetToFirstPage();
    }

    ariaSort(field: SkillSortField): AriaSortDirection {
        const current = this.sort();
        if (current.field !== field) {
            return AriaSortDirection.None;
        }
        return current.direction === SortDirection.Asc ? AriaSortDirection.Ascending : AriaSortDirection.Descending;
    }

    sortIcon(field: SkillSortField): SortIcon {
        const current = this.sort();
        if (current.field !== field) {
            return SortIcon.None;
        }
        return current.direction === SortDirection.Asc ? SortIcon.Up : SortIcon.Down;
    }

    // ---------- Create / edit ----------

    openCreate(): void {
        this.dialogState.set({ mode: DialogMode.Create, skill: null });
    }

    openEdit(skill: SkillResponse): void {
        this.dialogState.set({ mode: DialogMode.Edit, skill });
    }

    closeDialog(): void {
        this.dialogState.set(null);
        this.saving.set(false);
    }

    // Duplicate code or name comes back as a business error; the interceptor shows it and the dialog stays open
    submit(body: SkillRequest): void {
        const state = this.dialogState();
        if (!state) {
            return;
        }
        this.saving.set(true);

        const request = state.mode === DialogMode.Edit && state.skill
            ? this.skillService.update(state.skill.id, body)
            : this.skillService.create(body);

        request.subscribe({
            next: saved => {
                this.closeDialog();
                if (state.mode === DialogMode.Edit) {
                    this.toast.success(`Skill ${saved.name} updated`);
                    this.load(false);
                    return;
                }
                this.toast.success(`Skill ${saved.name} added`);
                // A new skill re-sorts the list into catalogue order: display_order ASC, then name
                this.sort.set(SkillsComponent.DEFAULT_SORT);
                this.resetToFirstPage();
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Internals ----------

    private watchKeyword(): void {
        this.keywordInput.valueChanges.pipe(
            debounceTime(SkillsComponent.SEARCH_DEBOUNCE_MS),
            map(value => value.trim()),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(value => {
            if (value !== this.keyword()) {
                this.keyword.set(value);
                this.resetToFirstPage();
            }
        });
    }

    private resetToFirstPage(): void {
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.load(true);
    }
}