import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, HostListener, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CvService } from "../../../services/cv.service";
import { ToastService } from "../../../services/toast.service";
import { CV_LANGUAGE_LABELS, CvLanguage } from "../../../enums/cv-language.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { CvResponse } from "../../../dtos/cv.dto";
import { CvPageState } from "../../../models/cv-page.model";
import { CvSortField, SortDirection } from "../../../enums/sort-field.enum";

/*
 * Deleted CV with the one action that matters here: restore.
 *
 * - Restore fails for three distinct reasons and the employee needs to know which one - the slot
 * was taken, the parent profile is gone or mastership conflicts. Those come back as error codes
 * and the error interceptor renders the matching message, so this component deliberately shows no
 * error toast of its own.
 */
@Component({
    selector: 'app-cv-deleted-list',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, DatePipe],
    templateUrl: './cv-deleted-list.component.html',
    styleUrl: './cv-deleted-list.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvDeletedListComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;

    private readonly cvService = inject(CvService);
    private readonly toast = inject(ToastService);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly profilesRoute = '/' + AppRoute.Profiles;

    readonly cvs = signal<CvResponse[]>([]);
    readonly loading = signal(false);

    readonly pageState = signal<CvPageState>({
        index: 0,
        size: CvDeletedListComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly restoreTarget = signal<CvResponse | null>(null);
    readonly restoring = signal(false);
    readonly isRestoreClosing = signal(false);
    private restoreBackdropMouseDownTarget: EventTarget | null = null;

    ngOnInit(): void {
        this.load(true);
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();

        this.cvService.listDeleted({
            page: index,
            size,
            sortBy: CvSortField.DeletedAt,
            direction: SortDirection.Desc,
        }).subscribe({
            next: result => {
                this.cvs.set(result.content);
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

    // ---------- Restore ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.restoreTarget()) {
            this.cancelRestore();
        }
    }

    askRestore(cv: CvResponse): void {
        this.isRestoreClosing.set(false);
        this.restoreTarget.set(cv);
    }

    onRestoreBackdropMouseDown(event: MouseEvent): void {
        this.restoreBackdropMouseDownTarget = event.target;
    }

    onRestoreBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.restoreBackdropMouseDownTarget === event.currentTarget) {
            this.cancelRestore();
        }
        this.restoreBackdropMouseDownTarget = null;
    }

    cancelRestore(): void {
        if (this.isRestoreClosing() || this.restoring()) {
            return;
        }
        this.isRestoreClosing.set(true);
        setTimeout(() => {
            this.restoreTarget.set(null);
            this.restoring.set(false);
            this.isRestoreClosing.set(false);
        }, 500);
    }

    confirmRestore(): void {
        const target = this.restoreTarget();
        if (!target) {
            return;
        }
        this.restoring.set(true);

        this.cvService.restore(target.id).subscribe({
            next: restored => {
                this.closeRestoreDialog();
                this.toast.success(
                    `${this.languageLabels[restored.language]} CV of "${restored.profileName}" restored`
                );
                this.load(false);
            },
            // The interceptor names which of the three conditions blocked it; the row stays put.
            error: () => this.closeRestoreDialog(),
        });
    }

    languageLabel(language: CvLanguage): string {
        return this.languageLabels[language];
    }

    private closeRestoreDialog(): void {
        this.restoreTarget.set(null);
        this.restoring.set(false);
        this.isRestoreClosing.set(false);
    }
}