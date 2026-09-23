import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ApprovalService } from "../../../services/approval.service";
import { CV_LANGUAGE_LABELS, CvLanguage } from "../../../enums/cv-language.enum";
import { APPROVAL_LEVEL_DESCRIPTIONS, APPROVAL_LEVEL_LABELS } from "../../../enums/approval-level.enum";
import { CancelledReviewItem } from "../../../dtos/approval.dto";
import { ApprovalQueuePageState } from "../../../models/approval-queue.model";

/*
 * Reviews an administrator took off the signed-in reviewer's queue.
 * The queue drops a cancelled item silently - right for "what do I do next", but it leaves the
 * reviewer unsure whether they missed something. This list is where they read why.
 */
@Component({
    selector: 'app-cancelled-reviews',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, DatePipe],
    templateUrl: './cancelled-reviews.component.html',
    styleUrl: './cancelled-reviews.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CancelledReviewsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;

    private readonly approvalService = inject(ApprovalService);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly levelLabels = APPROVAL_LEVEL_LABELS;
    readonly levelDescriptions = APPROVAL_LEVEL_DESCRIPTIONS;

    readonly items = signal<CancelledReviewItem[]>([]);
    readonly loading = signal(false);

    readonly pageState = signal<ApprovalQueuePageState>({
        index: 0,
        size: CancelledReviewsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly isEmpty = computed(() => !this.loading() && this.items().length === 0);

    ngOnInit(): void {
        this.load(true);
    }

    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();

        this.approvalService.getCancelledReviews({ page: index, size }).subscribe({
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

    languageLabel(language: CvLanguage | null): string {
        return language ? this.languageLabels[language] : '-';
    }
}