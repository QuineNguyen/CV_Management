import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { TeamService } from "../../services/team.service";
import { ToastService } from "../../services/toast.service";
import { TeamDialogState, TeamPageState } from "../../models/team.model";
import { TeamRequest, TeamResponse } from "../../dtos/team.dto";
import { DialogMode } from "../../enums/dialog-mode.enum";
import { debounceTime, distinctUntilChanged } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { TeamFormDialogComponent } from "./team-form/team-form-dialog.component";
import { TeamMembersDialogComponent } from "./team-members/team-members-dialog.component";
import { AriaSortDirection, SortDirection, SortIcon, TeamSortField } from "../../enums/sort-field.enum";
import { SortState } from "../../models/sort-state.model";

@Component({
    selector: 'app-teams',
    standalone: true,
    imports: [
        ReactiveFormsModule,
        MatPaginatorModule,
        MatTooltipModule,
        TeamFormDialogComponent,
        TeamMembersDialogComponent,
    ],
    templateUrl: './teams.component.html',
    styleUrl: './teams.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 20;
    private static readonly SEARCH_DEBOUNCE_MS = 300;

    private readonly teamService = inject(TeamService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];

    readonly pageState = signal<TeamPageState>({
        index: 0,
        size: TeamsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly teams = signal<TeamResponse[]>([]);
    readonly loading = signal(false);

    readonly keyword = new FormControl('', { nonNullable: true });

    readonly dialogState = signal<TeamDialogState | null>(null);
    readonly saving = signal(false);

    readonly membersTarget = signal<TeamResponse | null>(null);

    readonly deleteTarget = signal<TeamResponse | null>(null);
    readonly deleting = signal(false);
    readonly isDeleteClosing = signal(false);

    readonly isEmpty = computed(() => !this.loading() && this.teams().length === 0);

    readonly sortField = TeamSortField;

    readonly sort = signal<SortState<TeamSortField> | null>(null);

    ngOnInit(): void {
        this.watchKeyword();
        this.loadTeams(true);
    }

    // ---------- Loading ----------

    loadTeams(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }

        const { index, size } = this.pageState();
        const activeSort = this.sort();

        this.teamService.search({
            keyword: this.keyword.value.trim() || undefined,
            sortBy: activeSort?.field,
            direction: activeSort?.direction,
            page: index,
            size,
        }).subscribe({
            next: result => {
                this.teams.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.loadTeams(true);
    }

    clearFilters(): void {
        this.keyword.setValue('', { emitEvent: false });
        this.sort.set(null);
        this.resetToFirstPage();
    }

    // Ascending on first click, then descending, then back to the default order
    toggleSort(field: TeamSortField): void {
        this.sort.update(current => {
            if (current?.field !== field) {
                return { field, direction: SortDirection.Asc };
            }
            return current.direction === SortDirection.Asc
                ? { field, direction: SortDirection.Desc }
                : null;
        });

        this.resetToFirstPage();
    }

    // ARIA value for a sortable header cell
    ariaSort(field: TeamSortField): AriaSortDirection.Ascending | AriaSortDirection.Descending | AriaSortDirection.None {
        const current = this.sort();
        if (current?.field !== field) {
            return AriaSortDirection.None;
        }
        return current.direction === SortDirection.Asc ? AriaSortDirection.Ascending : AriaSortDirection.Descending;
    }

    sortIcon(field: TeamSortField): SortIcon {
        const current = this.sort();
        if (current?.field !== field) {
            return SortIcon.None;
        }
        return current.direction === SortDirection.Asc ? SortIcon.Up : SortIcon.Down;
    }

    // Tooltip telling the user what the next click will do
    sortHint(field: TeamSortField): string {
        const current = this.sort();
        if (current?.field !== field) {
            return 'Sort ascending';
        }
        return current.direction === SortDirection.Asc ? 'Sort descending' : 'Clear sorting';
    }

    // ---------- Create / edit dialog -----------

    openCreate(): void {
        this.dialogState.set({ mode: DialogMode.Create, team: null });
    }

    openEdit(team: TeamResponse): void {
        this.dialogState.set({ mode: DialogMode.Edit, team });
    }

    closeDialog(): void {
        this.dialogState.set(null);
        this.saving.set(false);
    }

    submitDialog(body: TeamRequest): void {
        const state = this.dialogState();
        if (!state) {
            return;
        }

        this.saving.set(true);
        const isEdit = state.mode === DialogMode.Edit && state.team;

        const request$ = isEdit
            ? this.teamService.update(state.team!.id, body)
            : this.teamService.create(body);

        request$.subscribe({
            next: saved => {
                this.saving.set(false);
                this.closeDialog();
                this.toast.success(
                    isEdit ? `Team ${saved.code} updated` : `Team ${saved.code} created`
                );
                this.loadTeams(false);
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Members panel ----------

    openMembers(team: TeamResponse): void {
        this.membersTarget.set(team);
    }

    // Member count on the row is stale one membership changed
    closeMembers(changed: boolean): void {
        this.membersTarget.set(null);
        if (changed) {
            this.loadTeams(false);
        }
    }

    // ---------- Delete ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.deleteTarget()) {
            this.cancelDelete();
        }
    }

    askDelete(team: TeamResponse): void {
        this.isDeleteClosing.set(false);
        this.deleteTarget.set(team);
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
        this.teamService.delete(target.id).subscribe({
            next: () => {
                this.deleteTarget.set(null);
                this.deleting.set(false);
                this.isDeleteClosing.set(false);
                this.toast.success(`Deleted team ${target.code}`);
                this.loadTeams(false);
            },
            error: () => {
                this.deleting.set(false);
                this.deleteTarget.set(null);
                this.isDeleteClosing.set(false);
            }
        });
    }

    // ---------- Internals ----------

    private watchKeyword(): void {
        this.keyword.valueChanges.pipe(
            debounceTime(TeamsComponent.SEARCH_DEBOUNCE_MS),
            distinctUntilChanged(),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(() => this.resetToFirstPage());
    }

    private resetToFirstPage(): void {
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.loadTeams(true);
    }
}