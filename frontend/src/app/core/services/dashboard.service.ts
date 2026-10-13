import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { AdminDashboardResponse, EmployeeDashboardResponse, TechLeadDashboardResponse } from "../dtos/dashboard.dto";
import { environment } from "../../../environments/environment";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

@Injectable({ providedIn: 'root' })
export class DashboardService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    getAdminDashboard(): Observable<AdminDashboardResponse> {
        return this.http.get<AdminDashboardResponse>(this.url(ApiEndpoint.DashboardAdmin));
    }

    getTechLeadDashboard(): Observable<TechLeadDashboardResponse> {
        return this.http.get<TechLeadDashboardResponse>(this.url(ApiEndpoint.DashboardTechLead));
    }

    getEmployeeDashboard(): Observable<EmployeeDashboardResponse> {
        return this.http.get<EmployeeDashboardResponse>(this.url(ApiEndpoint.DashboardEmployee));
    }
}