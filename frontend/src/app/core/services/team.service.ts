import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { TeamMemberResponse, TeamRequest, TeamResponse, TeamSearchQuery } from "../dtos/team.dto";
import { Observable } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

@Injectable({ providedIn: 'root' })
export class TeamService {
    
    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    search(query: TeamSearchQuery): Observable<PagedResponse<TeamResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.keyword) {
            params = params.set('keyword', query.keyword);
        }

        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }

        if (query.direction) {
            params = params.set('direction', query.direction);
        }

        return this.http.get<PagedResponse<TeamResponse>>(this.url(ApiEndpoint.Team), { params });
    }

    getById(id: string): Observable<TeamResponse> {
        return this.http.get<TeamResponse>(this.url(`${ApiEndpoint.Team}/${id}`));
    }
    
    create(body: TeamRequest): Observable<TeamResponse> {
        return this.http.post<TeamResponse>(this.url(ApiEndpoint.Team), body);
    }

    update(id: string, body: TeamRequest): Observable<TeamResponse> {
        return this.http.put<TeamResponse>(this.url(`${ApiEndpoint.Team}/${id}`), body);
    }

    delete(id: string): Observable<void> {
        return this.http.delete<void>(this.url(`${ApiEndpoint.Team}/${id}`));
    }

    getMembers(teamId: string): Observable<TeamMemberResponse[]> {
        return this.http.get<TeamMemberResponse[]>(this.url(`${ApiEndpoint.Team}/${teamId}/members`));
    }

    addMember(teamId: string, userId: string): Observable<void> {
        return this.http.post<void>(this.url(`${ApiEndpoint.Team}/${teamId}/members/${userId}`), null);
    }

    removeMember(teamId: string, userId: string): Observable<void> {
        return this.http.delete<void>(this.url(`${ApiEndpoint.Team}/${teamId}/members/${userId}`));
    }
}