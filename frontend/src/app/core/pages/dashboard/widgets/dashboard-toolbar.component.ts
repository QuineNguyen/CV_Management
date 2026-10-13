import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, input, output } from "@angular/core";

/*
 * Page header shared by the three dashboards.
 * Extra actions are projected before the Refresh button.
 */
@Component({
    selector: 'app-dashboard-toolbar',
    standalone: true,
    imports: [DatePipe],
    templateUrl: './dashboard-toolbar.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardToolbarComponent {
    readonly heading = input.required<string>();
    readonly subtitle = input<string>('');
    // Server time of the figures; widgets are only as fresh as this
    readonly generatedAt = input<string | null>(null);
    readonly refreshing = input(false);

    readonly refresh = output<void>();
}