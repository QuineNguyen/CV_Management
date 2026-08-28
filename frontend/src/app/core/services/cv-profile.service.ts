import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { CvProfileQuery, CvProfileRequest, CvProfileResponse } from "../dtos/cv-profile.dto";
import { Observable } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { EmployeeTeamResponse } from "../dtos/employee-team.dto";

@Injectable({ providedIn: 'root' })
export class CvProfileService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    private profilesOf(employeeId: string): string {
        return `${ApiEndpoint.Employees}/${employeeId}/cv-profiles`;
    }

    listByEmployee(query: CvProfileQuery): Observable<PagedResponse<CvProfileResponse>> {
        const params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        return this.http.get<PagedResponse<CvProfileResponse>>(
            this.url(this.profilesOf(query.employeeId)), { params }
        );
    }

    listAssignableTeams(employeeId: string): Observable<EmployeeTeamResponse[]> {
        return this.http.get<EmployeeTeamResponse[]>(
            this.url(`${this.profilesOf(employeeId)}/team-options`)
        );
    }

    getById(id: string): Observable<CvProfileResponse> {
        return this.http.get<CvProfileResponse>(this.url(`${ApiEndpoint.Profiles}/${id}`));
    }

    create(employeeId: string, body: CvProfileRequest): Observable<CvProfileResponse> {
        return this.http.post<CvProfileResponse>(this.url(this.profilesOf(employeeId)), body);
    }

    update(id: string, body: CvProfileRequest): Observable<CvProfileResponse> {
        return this.http.put<CvProfileResponse>(this.url(`${ApiEndpoint.Profiles}/${id}`), body);
    }

    delete(id: string): Observable<void> {
        return this.http.delete<void>(this.url(`${ApiEndpoint.Profiles}/${id}`));
    }

    setPrimary(id: string): Observable<CvProfileResponse> {
        return this.http.post<CvProfileResponse>(this.url(`${ApiEndpoint.Profiles}/${id}/set-primary`), {});
    }

    // Idempotent: returns the current primary profile, creating the first one if none exists.
    ensureProfile(employeeId: string): Observable<CvProfileResponse> {
        return this.http.post<CvProfileResponse>(this.url(`${this.profilesOf(employeeId)}/ensure`), {});
    }
}