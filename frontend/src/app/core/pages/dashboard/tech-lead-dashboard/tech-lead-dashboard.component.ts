import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit } from "@angular/core";
import { Params, RouterLink } from "@angular/router";
import { DashboardService } from "../../../services/dashboard.service";
import { AuthService } from "../../../services/auth.service";
import { DashboardState } from "../dashboard-state";
import { TechLeadDashboardResponse } from "../../../dtos/dashboard.dto";
import { ToastService } from "../../../services/toast.service";
import { DashboardFilter, DashboardIcon, DashboardQueryParam, DashboardScope } from "../../../enums/dashboard.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { DashboardToolbarComponent } from "../widgets/dashboard-toolbar.component";
import { OverdueRequestsWidgetComponent } from "../widgets/overdue-requests-widget.component";

/*
 * Dashboard of a tech lead: the teams they lead and their own review queue.
 * No SLA table here - the queue card already says what is late for this reviewer.
 */
@Component({
    selector: 'app-tech-lead-dashboard',
    standalone: true,
    imports: [RouterLink, DashboardToolbarComponent, OverdueRequestsWidgetComponent],
    templateUrl: './tech-lead-dashboard.component.html',
    styleUrl: './tech-lead-dashboard.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TechLeadDashboardComponent implements OnInit {
    private readonly dashboardService = inject(DashboardService);
    private readonly auth = inject(AuthService);

    readonly state = new DashboardState<TechLeadDashboardResponse>(
        () => this.dashboardService.getTechLeadDashboard(),
        inject(ToastService),
        inject(DestroyRef),
    );

    readonly icons = DashboardIcon;
    readonly links = {
        users: '/' + AppRoute.Users,
        cvSearch: '/' + AppRoute.CvsSearch,
        updateRequests: '/' + AppRoute.UpdateRequests,
        approvalQueue: '/' + AppRoute.ApprovalQueue,
    };
    readonly teamParams: Params = { [DashboardQueryParam.Scope]: DashboardScope.Team };
    readonly teamOverdueParams: Params = {
        [DashboardQueryParam.Scope]: DashboardScope.Team,
        [DashboardQueryParam.Filter]: DashboardFilter.Overdue,
    };

    readonly subtitle = computed(() => {
        const name = this.auth.user()?.fullName;
        return name ? `Welcome back, ${name} · the teams you lead` : 'The teams you lead';
    });

    ngOnInit(): void {
        this.state.load();
    }
}