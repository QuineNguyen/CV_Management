import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal, WritableSignal } from "@angular/core";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { SortState } from "../../../models/sort-state.model";
import { AriaSortDirection, CvSearchSortField, SortDirection, SortIcon } from "../../../enums/sort-field.enum";
import { CvService } from "../../../services/cv.service";
import { DepartmentService } from "../../../services/department.service";
import { AuthService } from "../../../services/auth.service";
import { Router } from "@angular/router";
import { CV_LANGUAGE_LABELS, CvLanguage } from "../../../enums/cv-language.enum";
import { CvRouteSegment, CvSearchDropdown, ProfileScope } from "../../../enums/cv-search.enum";
import { CvSearchFilterOption, CvSearchItemResponse, CvSearchSkillTag } from "../../../dtos/cv.dto";
import { DepartmentNode } from "../../../dtos/department.dto";
import { CvPageState } from "../../../models/cv-page.model";
import { SkillOptionResponse } from "../../../dtos/skill.dto";
import { UserRole } from "../../../enums/user-role.enum";
import { catchError, debounceTime, EMPTY, map } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AppRoute } from "../../../enums/app-route.enum";
import { AvatarView } from "../../../models/user-page.model";
import { SkillFilterComponent } from "./skill-filter/skill-filter.component";
import { untranslatedLabelOf } from "../../../models/cv-language-slot.model";

/*
 * CV search.
 * - Organization criteria keep never-published CVs, labelled "No official version yet";
 * skills and content read the latest published version only, so those CVs drop out.
 * - Role scope and every filter are applied by the server. This page never trims the rows it
 * receives - it only renders them.
 * - A row whose CV still has placeholders to translate says how many, under its language.
 */
@Component({
    selector: 'app-cv-search',
    standalone: true,
    imports: [ReactiveFormsModule, MatPaginatorModule, MatTooltipModule, DatePipe, SkillFilterComponent],
    templateUrl: './cv-search.component.html',
    styleUrl: './cv-search.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvSearchComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;
    private static readonly SEARCH_DEBOUNCE_MS = 300;
    private static readonly AVATAR_TONES = 6;
    private static readonly VISIBLE_SKILLS = 4;
    private static readonly DEFAULT_SORT: SortState<CvSearchSortField> = {
        field: CvSearchSortField.EmployeeName,
        direction: SortDirection.Asc,
    };
    private static readonly DEPARTMENT_LOOKUP_SIZE = 100;

    private readonly cvService = inject(CvService);
    private readonly departmentService = inject(DepartmentService);
    private readonly auth = inject(AuthService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly languageOptions = Object.values(CvLanguage);
    readonly dropdown = CvSearchDropdown;
    readonly profileScope = ProfileScope;
    readonly sortField = CvSearchSortField;
    readonly untranslatedLabelOf = untranslatedLabelOf;

    readonly results = signal<CvSearchItemResponse[]>([]);
    readonly loading = signal(false);
    readonly pageState = signal<CvPageState>({
        index: 0,
        size: CvSearchComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly departments = signal<DepartmentNode[]>([]);
    readonly teams = signal<CvSearchFilterOption[]>([]);

    // Ids whose presigned avatar URL failed to load; reset on every refetch
    private readonly brokenAvatars = signal<ReadonlySet<string>>(new Set<string>());

    // Only the newest response is shown when filters change quickly
    private requestSeq = 0;

    // Text inputs; the debounced, trimmed value is what the query uses
    readonly employeeNameInput = new FormControl('', { nonNullable: true });
    readonly profileNameInput = new FormControl('', { nonNullable: true });
    readonly keywordInput = new FormControl('', { nonNullable: true });
    readonly employeeName = signal('');
    readonly profileName = signal('');
    readonly keyword = signal('');

    readonly departmentFilter = signal<string | null>(null);
    readonly teamFilter = signal<string | null>(null);
    readonly languageFilter = signal<CvLanguage | null>(null);
    readonly profileScopeFilter = signal<ProfileScope>(ProfileScope.Primary);
    readonly selectedSkills = signal<SkillOptionResponse[]>([]);
    readonly expandedSkills = signal<ReadonlySet<string>>(new Set<string>());
    readonly openDropdown = signal<CvSearchDropdown | null>(null);

    readonly sort = signal<SortState<CvSearchSortField>>(CvSearchComponent.DEFAULT_SORT);

    // Employees only ever see their own CVs, so people and organization filters would be noise
    readonly showPeopleFilters = computed(() => this.auth.hasRole(UserRole.Admin, UserRole.HR, UserRole.TechLead));

    readonly currentUserId = computed(() => this.auth.user()?.id ?? null);

    readonly subtitle = computed(() => {
        const total = this.pageState().total;
        if (this.auth.hasRole(UserRole.Admin, UserRole.HR)) {
            return `${total} CV(s) across the company`;
        }
        if (this.auth.hasRole(UserRole.TechLead)) {
            return `${total} CV(s) in the teams you lead`;
        }
        return `${total} of your CV(s)`;
    });

    // Skills or text in play: never-published CVs are excluded by the server
    readonly hasContentCriteria = computed(() => this.keyword().length > 0 || this.selectedSkills().length > 0);

    // The server searches every profile when a profile name is given
    readonly profileNameActive = computed(() => this.profileName().length > 0);

    readonly selectedSkillIds = computed(() => new Set(this.selectedSkills().map(skill => skill.id)));

    readonly activeFilterCount = computed(() => {
        const values: unknown[] = [
            this.employeeName(),
            this.profileName(),
            this.keyword(),
            this.departmentFilter(),
            this.teamFilter(),
            this.languageFilter(),
        ];
        return values.filter(value => !!value).length
            + (this.profileScopeFilter() === ProfileScope.All ? 1 : 0)
            + (this.selectedSkills().length ? 1 : 0);
    });

    readonly departmentLabel = computed(() => this.optionLabel(this.departments(), this.departmentFilter(), 'All departments'));
    readonly teamLabel = computed(() => this.optionLabel(this.teams(), this.teamFilter(), 'All teams'));

    readonly languageLabel = computed(() => {
        const language = this.languageFilter();
        return language ? this.languageLabels[language] : 'All languages';
    });

    readonly isEmpty = computed(() => !this.loading() && this.results().length === 0);

    ngOnInit(): void {
        if (this.showPeopleFilters()) {
            this.loadFilterOptions();
        }
        this.watchText(this.employeeNameInput, this.employeeName);
        this.watchText(this.profileNameInput, this.profileName);
        this.watchText(this.keywordInput, this.keyword);
        this.load(true);
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const seq = ++this.requestSeq;
        const { index, size } = this.pageState();
        const activeSort = this.sort();

        this.cvService.search({
            employeeName: this.employeeName() || undefined,
            profileName: this.profileName() || undefined,
            keyword: this.keyword() || undefined,
            departmentId: this.departmentFilter() ?? undefined,
            teamId: this.teamFilter() ?? undefined,
            language: this.languageFilter() ?? undefined,
            allProfiles: this.profileScopeFilter() === ProfileScope.All,
            skillIds: this.selectedSkills().map(skill => skill.id),
            sortBy: activeSort.field,
            direction: activeSort.direction,
            page: index,
            size,
        }).subscribe({
            next: result => {
                if (seq !== this.requestSeq) {
                    return;
                }
                this.results.set(result.content);
                this.expandedSkills.set(new Set<string>());
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.brokenAvatars.set(new Set<string>());
                this.loading.set(false);
            },
            error: () => {
                if (seq === this.requestSeq) {
                    this.loading.set(false);
                }
            },
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.load(true);
    }

    // ---------- Filters ----------

    toggleDropdown(kind: CvSearchDropdown, event: MouseEvent): void {
        event.stopPropagation();
        this.openDropdown.update(current => (current === kind ? null : kind));
    }

    selectDepartment(id: string | null): void {
        this.applyFilter(() => this.departmentFilter.set(id));
    }

    selectTeam(id: string | null): void {
        this.applyFilter(() => this.teamFilter.set(id));
    }

    selectLanguage(language: CvLanguage | null): void {
        this.applyFilter(() => this.languageFilter.set(language));
    }

    setProfileScope(scope: ProfileScope): void {
        if (this.profileScopeFilter() !== scope) {
            this.applyFilter(() => this.profileScopeFilter.set(scope));
        }
    }

    onSkillsChange(skills: SkillOptionResponse[]): void {
        this.applyFilter(() => this.selectedSkills.set(skills));
    }

    clearFilters(): void {
        for (const control of [this.employeeNameInput, this.profileNameInput, this.keywordInput]) {
            control.setValue('', { emitEvent: false });
        }
        this.employeeName.set('');
        this.profileName.set('');
        this.keyword.set('');
        this.departmentFilter.set(null);
        this.teamFilter.set(null);
        this.languageFilter.set(null);
        this.profileScopeFilter.set(ProfileScope.Primary);
        this.selectedSkills.set([]);
        this.sort.set(CvSearchComponent.DEFAULT_SORT);
        this.openDropdown.set(null);
        this.resetToFirstPage();
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        if (this.openDropdown()) {
            this.openDropdown.set(null);
        }
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.closeDropdowns();
    }

    // ---------- Sorting ----------

    // Name toggles Asc <-> Desc; publish date goes newest first, oldest first, then back to name
    toggleSort(field: CvSearchSortField): void {
        this.sort.update(current => {
            if (field === CvSearchSortField.EmployeeName) {
                return current.field === field && current.direction === SortDirection.Asc
                    ? { field, direction: SortDirection.Desc }
                    : { field, direction: SortDirection.Asc };
            }
            if (current.field !== field) {
                return { field, direction: SortDirection.Desc };
            }
            return current.direction === SortDirection.Desc
                ? { field, direction: SortDirection.Asc }
                : CvSearchComponent.DEFAULT_SORT;
        });
        this.resetToFirstPage();
    }

    ariaSort(field: CvSearchSortField): AriaSortDirection {
        const current = this.sort();
        if (current.field !== field) {
            return AriaSortDirection.None;
        }
        return current.direction === SortDirection.Asc ? AriaSortDirection.Ascending : AriaSortDirection.Descending;
    }

    sortIcon(field: CvSearchSortField): SortIcon {
        const current = this.sort();
        if (current.field !== field) {
            return SortIcon.None;
        }
        return current.direction === SortDirection.Asc ? SortIcon.Up : SortIcon.Down;
    }

    // ---------- Row actions ----------

    // Opens the detail page, which itself says when no official version exists yet
    view(item: CvSearchItemResponse): void {
        void this.router.navigate(['/' + AppRoute.Cvs, item.cvId]);
    }

    // Only the owner writes CV content
    isOwn(item: CvSearchItemResponse): boolean {
        return item.employeeId === this.currentUserId();
    }

    editCvHint(item: CvSearchItemResponse): string {
        return this.isOwn(item) ? 'Edit CV' : 'Only the CV owner can edit this CV';
    }

    historyHint(item: CvSearchItemResponse): string {
        return item.hasPublishedVersion ? 'Version history' : 'No version published yet';
    }

    edit(item: CvSearchItemResponse): void {
        if (!this.isOwn(item)) {
            return;
        }
        void this.router.navigate(['/' + AppRoute.Cvs, item.cvId, CvRouteSegment.Edit]);
    }

    history(item: CvSearchItemResponse): void {
        if (!item.hasPublishedVersion) {
            return;
        }
        void this.router.navigate(['/' + AppRoute.Cvs, item.cvId, CvRouteSegment.History]);
    }

    // ---------- Row display ----------

    toggleSkills(cvId: string): void {
        this.expandedSkills.update(set => {
            const next = new Set(set);
            if (next.has(cvId)) {
                next.delete(cvId);
            } else {
                next.add(cvId);
            }
            return next;
        });
    }

    isSkillsExpanded(cvId: string): boolean {
        return this.expandedSkills().has(cvId);
    }

    visibleSkills(item: CvSearchItemResponse): CvSearchSkillTag[] {
        if (this.isSkillsExpanded(item.cvId)) {
            return item.skills;
        }
        return item.skills.slice(0, CvSearchComponent.VISIBLE_SKILLS);
    }

    hiddenSkillCount(item: CvSearchItemResponse): number {
        return Math.max(0, item.skills.length - CvSearchComponent.VISIBLE_SKILLS);
    }

    hiddenSkillNames(item: CvSearchItemResponse): string {
        return item.skills.slice(CvSearchComponent.VISIBLE_SKILLS).map(skill => skill.name).join(', ');
    }

    // Skills the user filtered by are highlighted in each row
    isMatchedSkill(skill: CvSearchSkillTag): boolean {
        return this.selectedSkillIds().has(skill.id);
    }

    languageName(language: CvLanguage): string {
        return this.languageLabels[language];
    }

    // A stored photo wins; otherwise the row falls back to generated initials
    avatarUrlOf(item: CvSearchItemResponse): string | null {
        return item.avatarUrl && !this.brokenAvatars().has(item.cvId) ? item.avatarUrl : null;
    }

    // A presigned URL can expire while the page is open, so a failed load degrades to initials
    onAvatarError(item: CvSearchItemResponse): void {
        this.brokenAvatars.update(ids => new Set(ids).add(item.cvId));
    }

    // Hashes the employee id, so one person keeps one colour across rows and renames
    avatarOf(item: CvSearchItemResponse): AvatarView {
        const parts = item.employeeName.trim().split(/\s+/);
        const initials = parts.length === 1
            ? parts[0].slice(0, 2)
            : parts[0][0] + parts[parts.length - 1][0];
        
        let hash = 0;
        for (const char of item.employeeId) {
            hash = (hash * 31 + char.charCodeAt(0)) % 997;
        }
        return { initials: initials.toLowerCase(), tone: hash % CvSearchComponent.AVATAR_TONES };
    }

    // ---------- Internals ----------

    private applyFilter(change: () => void): void {
        change();
        this.openDropdown.set(null);
        this.resetToFirstPage();
    }

    // Compared with the applied value, so retyping a value after "Clear" still searches
    private watchText(control: FormControl<string>, target: WritableSignal<string>): void {
        control.valueChanges.pipe(
            debounceTime(CvSearchComponent.SEARCH_DEBOUNCE_MS),
            map(value => value.trim()),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(value => {
            if (value !== target()) {
                target.set(value);
                this.resetToFirstPage();
            }
        });
    }

    private loadFilterOptions(): void {
        this.departmentService.search({
            page: 0,
            size: CvSearchComponent.DEPARTMENT_LOOKUP_SIZE,
        }).pipe(
            catchError(() => EMPTY),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => this.departments.set(page.content));

        this.cvService.getFilterOptions().pipe(
            catchError(() => EMPTY),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(options => {
            this.teams.set(options.teams);
        });
    }

    private optionLabel(options: readonly { id: string; code: string; name: string }[], id: string | null, fallback: string): string {
        const option = id ? options.find(item => item.id === id) : undefined;
        return option ? `${option.code} - ${option.name}` : fallback;
    }

    private resetToFirstPage(): void {
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.load(true);
    }
}