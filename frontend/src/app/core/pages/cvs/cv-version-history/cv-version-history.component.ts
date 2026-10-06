import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CvService } from "../../../services/cv.service";
import { ActivatedRoute, Router } from "@angular/router";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { VERSION_SOURCE_LABELS } from "../../../enums/version-source.enum";
import { DRAFT_STATUS_LABELS } from "../../../enums/draft-status.enum";
import { CvDetailResponse } from "../../../dtos/cv.dto";
import { CvVersionHistoryItem } from "../../../dtos/cv-version.dto";
import { CvPageState } from "../../../models/cv-page.model";
import { VersionPick } from "../../../models/cv-diff.model";
import { leaveIfAccessDenied } from "../../../utils/access-denied.util";
import { CvVersionSortField, SortDirection } from "../../../enums/sort-field.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { QueryParam } from "../../../enums/query-param.enum";

/*
 * Version timeline of one CV, newest first, with the open draft pinned above it.
 * - Ticking two versions opens the diff viewer. Picks are kept by id, so a version ticked
 * on page 1 is still picked on page 3.
 * - Each row compares with its predecessor in one click; v1 compares with an empty CV.
 * - ROLLBACK rows say their author and reviewers are inherited from the source version.
 */
@Component({
    selector: 'app-cv-version-history',
    standalone: true,
    imports: [DatePipe, MatPaginatorModule, MatTooltipModule],
    templateUrl: './cv-version-history.component.html',
    styleUrl: './cv-version-history.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvVersionHistoryComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;
    private static readonly MAX_PICKS = 2;

    private readonly cvService = inject(CvService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly sourceLabels = VERSION_SOURCE_LABELS;
    readonly draftStatusLabels = DRAFT_STATUS_LABELS;

    readonly detail = signal<CvDetailResponse | null>(null);
    readonly loading = signal(true);

    readonly versions = signal<CvVersionHistoryItem[]>([]);
    readonly versionsLoading = signal(true);
    readonly pageState = signal<CvPageState>({
        index: 0,
        size: CvVersionHistoryComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly picks = signal<VersionPick[]>([]);
    readonly sortedPicks = computed(() => [...this.picks()].sort((a, b) => a.versionNumber - b.versionNumber));
    readonly pickLimitReached = computed(() => this.picks().length >= CvVersionHistoryComponent.MAX_PICKS);
    readonly currentVersionNumber = computed(() => this.detail()?.currentVersion?.versionNumber ?? null);

    private cvId: string | null = null;

    ngOnInit(): void {
        this.cvId = this.route.snapshot.paramMap.get('id');
        if (this.cvId) {
            this.loadDetail(this.cvId);
        }
    }

    // ---------- Loading ----------

    // Detail first: a reader without access leaves before a second request fails the same way
    private loadDetail(id: string): void {
        this.cvService.getById(id).subscribe({
            next: detail => {
                this.detail.set(detail);
                this.loading.set(false);
                this.loadPage();
            },
            error: err => {
                if (!leaveIfAccessDenied(err, this.router)) {
                    this.loading.set(false);
                }
            }
        });
    }

    private loadPage(): void {
        const id = this.cvId;
        if (!id) {
            return;
        }
        const { index, size } = this.pageState();
        this.versionsLoading.set(true);

        this.cvService.listVersions(id, {
            page: index,
            size,
            sortBy: CvVersionSortField.VersionNumber,
            direction: SortDirection.Desc,
        }).subscribe({
            next: result => {
                this.versions.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.versionsLoading.set(false);
            },
            error: () => this.versionsLoading.set(false),
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.loadPage();
    }

    // ---------- Picking ----------

    isPicked(id: string): boolean {
        return this.picks().some(pick => pick.id === id);
    }

    canPick(id: string): boolean {
        return this.isPicked(id) || !this.pickLimitReached();
    }

    togglePick(version: CvVersionHistoryItem): void {
        this.picks.update(picks => {
            if (picks.some(pick => pick.id === version.id)) {
                return picks.filter(pick => pick.id !== version.id);
            }
            return picks.length < CvVersionHistoryComponent.MAX_PICKS
                ? [...picks, { id: version.id, versionNumber: version.versionNumber }]
                : picks;
        });
    }

    unpick(id: string): void {
        this.picks.update(picks => picks.filter(pick => pick.id !== id));
    }

    // ---------- Navigation ----------

    // Older version on the left, whatever order they were ticked in
    compareSelected(): void {
        const [older, newer] = this.sortedPicks();
        if (older && newer) {
            this.openDiff(older.id, newer.id);
        }
    }

    compareWithPrevious(version: CvVersionHistoryItem): void {
        this.openDiff(version.previousVersionId, version.id);
    }

    compareTooltip(version: CvVersionHistoryItem): string {
        return version.previousVersionId
            ? `Compare with v${version.versionNumber - 1}`
            : `Show what v${version.versionNumber} introduced`;
    }

    backToCv(): void {
        if (this.cvId) {
            void this.router.navigate(['/' + AppRoute.Cvs, this.cvId]);
        }
    }

    // A null "from" is dropped from the URL, which the viewer reads as "compare with an empty CV"
    private openDiff(fromId: string | null, toId: string): void {
        if (!this.cvId) {
            return;
        }
        void this.router.navigate(['/' + AppRoute.Cvs, this.cvId, 'diff'], {
            queryParams: { [QueryParam.From]: fromId, [QueryParam.To]: toId },
        });
    }
}