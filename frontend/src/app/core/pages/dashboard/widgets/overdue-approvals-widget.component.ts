import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, input } from "@angular/core";
import { RouterLink } from "@angular/router";
import { ApprovalOverdueSummary, ApprovalSlaWidget } from "../../../dtos/dashboard.dto";
import { AppRoute } from "../../../enums/app-route.enum";
import { APPROVAL_LEVEL_LABELS, ApprovalLevel, UrgencyLevel } from "../../../enums/dashboard.enum";
import { pluralize } from "../../../utils/plural.util";

/*
 * Reviews past their SLA (Admin/HR only).
 *
 * "View all" opens Drafts Under Review rather than the approval queue: the queue lists only the
 * viewer's own assignments, while reassigning someone else's stalled review happens there.
 */
@Component({
    selector: 'app-overdue-approvals-widget',
    standalone: true,
    imports: [DatePipe, RouterLink],
    templateUrl: './overdue-approvals-widget.component.html',
    styleUrl: './overdue-approvals-widget.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverdueApprovalsWidgetComponent {
    readonly data = input.required<ApprovalSlaWidget>();
    readonly viewAllRoute = input<string>('/' + AppRoute.PendingDrafts);

    // Every row here is past its deadline, so all badges share the overdue colour
    readonly urgency = UrgencyLevel;

    levelLabel(level: number): string {
        return APPROVAL_LEVEL_LABELS[level as ApprovalLevel] ?? `Level ${level}`;
    }

    overdueLabel(item: ApprovalOverdueSummary): string {
        return item.daysOverdue > 0 ? `${pluralize(item.daysOverdue, 'day')} late` : 'Under a day late';
    }
}