import { ChangeDetectionStrategy, Component, computed, inject } from "@angular/core";
import { AuthService } from "../../services/auth.service";
import { UserRole } from "../../enums/user-role.enum";
import { AdminDashboardComponent } from "./admin-dashboard/admin-dashboard.component";
import { TechLeadDashboardComponent } from "./tech-lead-dashboard/tech-lead-dashboard.component";
import { EmployeeDashboardComponent } from "./employee-dashboard/employee-dashboard.component";

/*
 * Home screen: picks the dashboard for the signed-in role.
 * The choice is presentational; each endpoint checks the role again on the server.
 */
@Component({
    selector: 'app-dashboard',
    standalone: true,
    imports: [AdminDashboardComponent, TechLeadDashboardComponent, EmployeeDashboardComponent],
    templateUrl: './dashboard.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
    private readonly auth = inject(AuthService);

    readonly role = computed(() => this.auth.user()?.role ?? null);
    readonly roles = UserRole;
}