import { DatePipe, NgTemplateOutlet } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CvService } from "../../../services/cv.service";
import { ActivatedRoute, Router } from "@angular/router";
import { CHANGE_TYPE_LABELS, CHANGE_TYPE_MARKS, ChangeType, DIFF_VIEW_MODE_LABELS, DiffChunkType, DiffPickerSide, DiffValueKind, DiffViewMode } from "../../../enums/cv-diff.enum";
import { VERSION_SOURCE_LABELS } from "../../../enums/version-source.enum";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { CvDetailResponse } from "../../../dtos/cv.dto";
import { CvVersionHistoryItem, VersionDiffResponse, VersionDiffSide } from "../../../dtos/cv-version.dto";
import { DiffItemBlock, DiffSectionBlock, VersionPair } from "../../../models/cv-diff.model";
import { buildDiffSections, keepChanges } from "../../../utils/cv-diff-view.util";
import { catchError, filter, map, Observable, of, switchMap, tap } from "rxjs";
import { leaveIfAccessDenied } from "../../../utils/access-denied.util";
import { PagedResponse } from "../../../dtos/page.dto";
import { CvVersionSortField, SortDirection } from "../../../enums/sort-field.enum";
import { QueryParam } from "../../../enums/query-param.enum";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AppRoute } from "../../../enums/app-route.enum";

/*
 * Side-by-side diff of two published versions: older on the left, newer on the right.
 * - The URL holds the pair (?from=&to=), so a comparison can be bookmarked or shared. No "to"
 * opens the default pair - the current version against its predecessor. "to" without "from"
 * compares with an empty CV, which is how v1 shows what it introduced.
 * - The pickers page through the history on demand instead of loading every version up front.
 * - Changed sections and entries start open; unchanged ones start closed in the full view.
 */
@Component({
    selector: 'app-cv-diff-viewer',
    standalone: true,
    imports: [DatePipe, NgTemplateOutlet, MatTooltipModule],
    templateUrl: './cv-diff-viewer.component.html',
    styleUrl: './cv-diff-viewer.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvDiffViewerComponent implements OnInit {

    private static readonly PICKER_PAGE_SIZE = 20;

    private readonly cvService = inject(CvService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    readonly ChangeType = ChangeType;
    readonly ChunkType = DiffChunkType;
    readonly ValueKind = DiffValueKind;
    readonly PickerSide = DiffPickerSide;
    readonly ViewMode = DiffViewMode;
    readonly viewModes = Object.values(DiffViewMode);
    readonly viewModeLabels = DIFF_VIEW_MODE_LABELS;
    readonly changeLabels = CHANGE_TYPE_LABELS;
    readonly changeMarks = CHANGE_TYPE_MARKS;
    readonly sourceLabels = VERSION_SOURCE_LABELS;
    readonly languageLabels = CV_LANGUAGE_LABELS;

    readonly detail = signal<CvDetailResponse | null>(null);
    readonly loading = signal(true);

    // ---------- Pickers ----------

    readonly options = signal<CvVersionHistoryItem[]>([]);
    readonly optionsTotal = signal(0);
    readonly optionsLoading = signal(false);
    readonly hasMoreOptions = computed(() => this.options().length < this.optionsTotal());
    readonly openPicker = signal<DiffPickerSide | null>(null);
    private optionsPage = 0;

    // Pending choice; a null "from" means an empty CV
    readonly fromId = signal<string | null>(null);
    readonly toId = signal<string | null>(null);

    readonly sameVersionPicked = computed(() => !!this.toId() && this.fromId() === this.toId());

    readonly canCompare = computed(() => {
        const to = this.toId();
        const applied = this.applied();
        if (!to || this.sameVersionPicked()) {
            return false;
        }
        // Nothing to do when the pickers already match what is on screen
        return !(applied && applied.to === to && applied.from === this.fromId());
    });

    // ---------- Result ----------

    readonly applied = signal<VersionPair | null>(null);
    readonly diff = signal<VersionDiffResponse | null>(null);
    readonly diffLoading = signal(false);
    readonly viewMode = signal(DiffViewMode.ChangesOnly);

    // Sections and entries the reader opened or closed against their default
    private readonly flipped = signal<ReadonlySet<string>>(new Set());

    readonly allSections = computed<DiffSectionBlock[]>(() => {
        const diff = this.diff();
        const detail = this.detail();
        return diff && detail ? buildDiffSections(diff, detail.cv.language) : [];
    });

    readonly sections = computed(() => this.viewMode() === DiffViewMode.ChangesOnly
        ? keepChanges(this.allSections())
        : this.allSections());

    readonly identical = computed(() => {
        const stats = this.diff()?.stats;
        return !!stats && stats.added + stats.modified + stats.removed === 0;
    });

    // Short side names for the column headers and on phones for each cell
    readonly fromName = computed(() => this.sideName(this.diff()?.fromVersion ?? null));
    readonly toName = computed(() => this.sideName(this.diff()?.toVersion ?? null));

    private cvId = '';

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (!id) {
            return;
        }
        this.cvId = id;

        // Detail first: a reader without access leaves before anything else is asked for
        this.cvService.getById(id).pipe(
            tap(detail => this.detail.set(detail)),
            switchMap(() => this.fetchOptions(0)),
        ).subscribe({
            next: result => {
                this.acceptOptions(result, 0);
                this.loading.set(false);
                this.watchPair();
            },
            error: err => {
                if (!leaveIfAccessDenied(err, this.router)) {
                    this.loading.set(false);
                }
            },
        });
    }

    // ---------- Pair from the URL ----------

    private watchPair(): void {
        this.route.queryParamMap.pipe(
            map(params => ({ from: params.get(QueryParam.From), to: params.get(QueryParam.To) })),
            tap(pair => {
                if (!pair.to) {
                    this.openDefaultPair();
                }
            }),
            filter((pair): pair is VersionPair => !!pair.to),
            tap(pair => {
                this.fromId.set(pair.from);
                this.toId.set(pair.to);
                this.applied.set(pair);
                this.flipped.set(new Set());
                this.diffLoading.set(true);
            }),
            // switchMap drops a slower, older answer when the pair changes again
            switchMap(pair => this.cvService.diffVersions(this.cvId, pair.to, pair.from).pipe(
                // The interceptor already showed why; the page falls back to its empty state
                catchError(() => of(null)),
            )),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(diff => {
            this.diff.set(diff);
            this.diffLoading.set(false);
        });
    }

    // Current version against its predecessor; replaces the URL so Back skips the bare one
    private openDefaultPair(): void {
        const latest = this.options()[0];
        if (latest) {
            this.navigatePair({ from: latest.previousVersionId, to: latest.id }, true);
        }
    }

    // A null "from" is dropped from the URL
    private navigatePair(pair: VersionPair, replaceUrl: boolean): void {
        void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { [QueryParam.From]: pair.from, [QueryParam.To]: pair.to },
            replaceUrl,
        });
    }

    // ---------- Pickers ----------

    togglePicker(side: DiffPickerSide, event: MouseEvent): void {
        event.stopPropagation();
        this.openPicker.update(current => (current === side ? null : side));
    }

    choose(side: DiffPickerSide, id: string | null): void {
        if (side === DiffPickerSide.From) {
            this.fromId.set(id);
        } else if (id) {
            this.toId.set(id);
        }
        this.openPicker.set(null);
    }

    selectedOf(side: DiffPickerSide): string | null {
        return side === DiffPickerSide.From ? this.fromId() : this.toId();
    }

    pickerLabel(side: DiffPickerSide): string {
        const id = this.selectedOf(side);
        if (!id) {
            return side === DiffPickerSide.From ? 'Empty CV (before v1)' : 'Choose a version';
        }
        const versionNumber = this.versionNumberOf(id);
        return versionNumber === null ? 'Selected version' : `v${versionNumber}`;
    }

    loadMoreOptions(): void {
        if (this.optionsLoading() || !this.hasMoreOptions()) {
            return;
        }
        const next = this.optionsPage + 1;
        this.optionsLoading.set(true);

        this.fetchOptions(next).subscribe({
            next: result => {
                this.acceptOptions(result, next);
                this.optionsLoading.set(false);
            },
            error: () => this.optionsLoading.set(false),
        });
    }

    // Older version on the left, whatever order the pickers were set in
    compare(): void {
        const to = this.toId();
        if (!to || !this.canCompare()) {
            return;
        }
        const from = this.fromId();
        const pair = from && this.isNewer(from, to) ? { from: to, to: from } : { from, to };
        this.navigatePair(pair, false);
    }

    @HostListener('document:click')
    closePicker(): void {
        this.openPicker.set(null);
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.openPicker.set(null);
    }

    // ---------- Change labels ----------

    // Typed parameter: ng-template context variables are `any`, which cannot index a Record
    changeLabel(type: ChangeType): string {
        return CHANGE_TYPE_LABELS[type];
    }

    changeMark(type: ChangeType): string {
        return CHANGE_TYPE_MARKS[type];
    }

    // ---------- Expand / collapse ----------

    isOpen(key: string, openByDefault: boolean): boolean {
        return this.flipped().has(key) !== openByDefault;
    }

    toggle(key: string): void {
        this.flipped.update(keys => {
            const next = new Set(keys);
            if (!next.delete(key)) {
                next.add(key);
            }
            return next;
        });
    }

    sectionKeyOf(section: DiffSectionBlock): string {
        return `section:${section.key}`;
    }

    itemKeyOf(section: DiffSectionBlock, item: DiffItemBlock): string {
        return `${section.key}:${item.id}`;
    }

    setViewMode(mode: DiffViewMode): void {
        this.viewMode.set(mode);
        this.flipped.set(new Set());
    }

    backToHistory(): void {
        void this.router.navigate(['/' + AppRoute.Cvs, this.cvId, 'history']);
    }

    // ---------- Private helpers ----------

    private fetchOptions(page: number): Observable<PagedResponse<CvVersionHistoryItem>> {
        return this.cvService.listVersions(this.cvId, {
            page,
            size: CvDiffViewerComponent.PICKER_PAGE_SIZE,
            sortBy: CvVersionSortField.VersionNumber,
            direction: SortDirection.Desc,
        });
    }

    private acceptOptions(result: PagedResponse<CvVersionHistoryItem>, page: number): void {
        this.options.update(current => (page === 0 ? result.content : [...current, ...result.content]));
        this.optionsTotal.set(result.totalElements);
        this.optionsPage = page;
    }

    // Falls back to the result on screen for a deep-linked version not loaded into the picker yet
    private versionNumberOf(id: string): number | null {
        const option = this.options().find(candidate => candidate.id === id);
        if (option) {
            return option.versionNumber;
        }
        const applied = this.applied();
        const diff = this.diff();
        if (!applied || !diff) {
            return null;
        }
        if (id === applied.to) {
            return diff.toVersion.versionNumber;
        }
        return id === applied.from ? diff.fromVersion?.versionNumber ?? null : null;
    }

    private isNewer(a: string, b: string): boolean {
        const numberA = this.versionNumberOf(a);
        const numberB = this.versionNumberOf(b);
        return numberA !== null && numberB !== null && numberA > numberB;
    }

    private sideName(side: VersionDiffSide | null): string {
        return side ? `v${side.versionNumber}` : 'Empty CV';
    }
}