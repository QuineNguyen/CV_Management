import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DashboardService } from "../../../services/dashboard.service";
import { AuthService } from "../../../services/auth.service";
import { DashboardState } from "../dashboard-state";
import { EmployeeDashboardResponse } from "../../../dtos/dashboard.dto";
import { ToastService } from "../../../services/toast.service";
import { DashboardIcon } from "../../../enums/dashboard.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { DashboardToolbarComponent } from "../widgets/dashboard-toolbar.component";
import { OverdueRequestsWidgetComponent } from "../widgets/overdue-requests-widget.component";

/*
 * Personal dashboard: own CVs and the requests sent to the viewer.
 * No approval widget - an employee has no review to act on.
 */
@Component({
    selector: 'app-employee-dashboard',
    standalone: true,
    imports: [RouterLink, DashboardToolbarComponent, OverdueRequestsWidgetComponent],
    templateUrl: './employee-dashboard.component.html',
    styleUrl: './employee-dashboard.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeDashboardComponent implements OnInit {
    private readonly dashboardService = inject(DashboardService);
    private readonly auth = inject(AuthService);

    readonly state = new DashboardState<EmployeeDashboardResponse>(
        () => this.dashboardService.getEmployeeDashboard(),
        inject(ToastService),
        inject(DestroyRef),
    );

    readonly icons = DashboardIcon;
    readonly links = {
        profiles: '/' + AppRoute.Profiles,
        createCv: '/' + AppRoute.CvsNew,
        updateRequests: '/' + AppRoute.UpdateRequests,
    };

    readonly subtitle = computed(() => {
        const name = this.auth.user()?.fullName;
        return name ? `Welcome back, ${name}` : 'Your CVs and requests';
    });

    // Any request past its deadline makes the requests tile urgent
    readonly hasOverdue = computed(() => (this.state.data()?.requestDeadlines.overdueCount ?? 0) > 0);

    ngOnInit(): void {
        this.state.load();
    }
}