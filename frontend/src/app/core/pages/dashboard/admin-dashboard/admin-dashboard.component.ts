import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit } from "@angular/core";
import { Params, RouterLink } from "@angular/router";
import { DashboardService } from "../../../services/dashboard.service";
import { AuthService } from "../../../services/auth.service";
import { DashboardState } from "../dashboard-state";
import { AdminDashboardResponse } from "../../../dtos/dashboard.dto";
import { ToastService } from "../../../services/toast.service";
import { DashboardFilter, DashboardIcon, DashboardQueryParam } from "../../../enums/dashboard.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { DashboardToolbarComponent } from "../widgets/dashboard-toolbar.component";
import { OverdueRequestsWidgetComponent } from "../widgets/overdue-requests-widget.component";
import { OverdueApprovalsWidgetComponent } from "../widgets/overdue-approvals-widget.component";

// Company-wide dashboard, shared by Admin and HR
@Component({
    selector: 'app-admin-dashboard',
    standalone: true,
    imports: [
        RouterLink,
        DashboardToolbarComponent,
        OverdueRequestsWidgetComponent,
        OverdueApprovalsWidgetComponent,
    ],
    templateUrl: './admin-dashboard.component.html',
    styleUrl: './admin-dashboard.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminDashboardComponent implements OnInit {
    private readonly dashboardService = inject(DashboardService);
    private readonly auth = inject(AuthService);

    readonly state = new DashboardState<AdminDashboardResponse>(
        () => this.dashboardService.getAdminDashboard(),
        inject(ToastService),
        inject(DestroyRef),
    );

    readonly icons = DashboardIcon;
    readonly links = {
        users: '/' + AppRoute.Users,
        cvSearch: '/' + AppRoute.CvsSearch,
        updateRequests: '/' + AppRoute.UpdateRequests,
        profileRequests: '/' + AppRoute.ProfileUpdateRequests,
        pendingDrafts: '/' + AppRoute.PendingDrafts,
        approvalQueue: '/' + AppRoute.ApprovalQueue,
    };
    readonly overdueParams: Params = { [DashboardQueryParam.Filter]: DashboardFilter.Overdue };

    readonly subtitle = computed(() => {
        const name = this.auth.user()?.fullName;
        return name ? `Welcome back, ${name} · Company-wide view` : 'Company-wide view';
    });

    // Share of active CVs with an official version; drawn, not computed server-side
    readonly publishedShare = computed(() => {
        const data = this.state.data();
        if (!data || data.totalActiveCvs === 0) {
            return 0;
        }
        return Math.round((data.cvsWithVersion / data.totalActiveCvs) * 100);
    });

    ngOnInit(): void {
        this.state.load();
    }
}