import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ApprovalService } from "../../../services/approval.service";
import { Router } from "@angular/router";
import { CV_LANGUAGE_LABELS, CvLanguage } from "../../../enums/cv-language.enum";
import { APPROVAL_LEVEL_DESCRIPTIONS, APPROVAL_LEVEL_LABELS } from "../../../enums/approval-level.enum";
import { ApprovalQueueItem } from "../../../dtos/approval.dto";
import { ApprovalQueuePageState, slaLabelOf, slaToneOf } from "../../../models/approval-queue.model";
import { ApprovalSortField, SortDirection } from "../../../enums/sort-field.enum";
import { AppRoute } from "../../../enums/app-route.enum";

/*
 * The reviewer's inbox.
 * Shows only what is assigned to the signed-in person and that is a server-side scope rather than
 * a filter applied here. Exclusive assignment is what keeps two tech leads from deciding
 * the same CV at once, so a queue that showed a colleague's work would be misleading even if the
 * open action failed afterwards.
 * Sorted by deadline ascending by default: the queue answers "what do I do next" and that is the
 * item closest to breaching its SLA, not the one that arrived first.
 */
@Component({
    selector: 'app-approval-queue',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, DatePipe],
    templateUrl: './approval-queue.component.html',
    styleUrl: './approval-queue.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApprovalQueueComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;

    private readonly approvalService = inject(ApprovalService);
    private readonly router = inject(Router);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly levelLabels = APPROVAL_LEVEL_LABELS;
    readonly levelDescriptions = APPROVAL_LEVEL_DESCRIPTIONS;

    readonly items = signal<ApprovalQueueItem[]>([]);
    readonly loading = signal(false);

    readonly pageState = signal<ApprovalQueuePageState>({
        index: 0,
        size: ApprovalQueueComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly isEmpty = computed(() => !this.loading() && this.items().length === 0);

    // Drives the subtitle: how much of the queue is already past its deadline.
    readonly overdueCount = computed(
        () => this.items().filter(item => item.slaRemainingMinutes < 0).length
    );

    ngOnInit(): void {
        this.load(true);
    }

    // ---------- Loading ----------

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }

        const { index, size } = this.pageState();

        this.approvalService.getQueue({
            page: index,
            size,
            sortBy: ApprovalSortField.DueAt,
            direction: SortDirection.Asc,
        }).subscribe({
            next: result => {
                this.items.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.loading.set(false);
            },
            // The interceptor already surfaced the reason; the list keeps its previous contents.
            error: () => this.loading.set(false),
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.load(true);
    }

    // ---------- Navigation ----------

    openReview(item: ApprovalQueueItem): void {
        void this.router.navigate(['/' + AppRoute.Approvals, 'drafts', item.draftId, 'review']);
    }

    // ---------- Presentation ----------

    languageLabel(language: CvLanguage | null): string {
        return language ? this.languageLabels[language] : '-';
    }

    slaTone(item: ApprovalQueueItem): string {
        return slaToneOf(item.slaRemainingMinutes);
    }

    slaLabel(item: ApprovalQueueItem): string {
        return slaLabelOf(item.slaRemainingMinutes);
    }
}