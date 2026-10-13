import { DatePipe, NgTemplateOutlet } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, input } from "@angular/core";
import { Params, RouterLink } from "@angular/router";
import { OverdueRequestSummary, RequestDeadlineWidget } from "../../../dtos/dashboard.dto";
import { AppRoute } from "../../../enums/app-route.enum";
import { URGENCY_LABELS, UrgencyLevel } from "../../../enums/dashboard.enum";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { pluralize } from "../../../utils/plural.util";

/*
 * "Due soon / Overdue" widget: three tallies and the most urgent rows.
 * - Replaces the HR monitoring email, so it only draws what the server computed.
 * - showEmployee is off on the personal dashboard, where every row is the viewer's own.
 * - allowCreateCv is on for the owner only: a request without a CV is answered by creating one.
 */
@Component({
    selector: 'app-overdue-requests-widget',
    standalone: true,
    imports: [DatePipe, NgTemplateOutlet, RouterLink],
    templateUrl: './overdue-requests-widget.component.html',
    styleUrl: './overdue-requests-widget.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverdueRequestsWidgetComponent {

    readonly data = input.required<RequestDeadlineWidget>();
    readonly heading = input('Update request deadlines');
    readonly emptyHint = input('No pending update request is due in the coming days');
    readonly showEmployee = input(true);
    readonly allowCreateCv = input(false);
    readonly viewAllRoute = input<string>('/' + AppRoute.UpdateRequests);
    readonly viewAllParams = input<Params | null>(null);

    readonly urgency = UrgencyLevel;
    readonly urgencyLabels = URGENCY_LABELS;
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly cvsRoute = AppRoute.Cvs;
    readonly createCvRoute = '/' + AppRoute.CvsNew;

    readonly total = computed(() => {
        const data = this.data();
        return data.overdueCount + data.lastDayCount + data.approachingCount;
    });

    readonly hasMore = computed(() => this.total() > this.data().items.length);

    timingLabel(item: OverdueRequestSummary): string {
        switch (item.urgency) {
            case UrgencyLevel.Overdue:
                return item.daysOverdue > 0 ? `${pluralize(item.daysOverdue, 'day')} overdue` : this.urgencyLabels[item.urgency];
            case UrgencyLevel.LastDay:
                return this.urgencyLabels[item.urgency];
            default:
                return `Due in ${pluralize(-item.daysOverdue, 'day')}`;
        }
    }
}