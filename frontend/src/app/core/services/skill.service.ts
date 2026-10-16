import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { MoveSkillRequest, SkillOptionQuery, SkillOptionResponse, SkillRequest, SkillResponse, SkillSearchQuery } from "../dtos/skill.dto";
import { Observable } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

@Injectable({ providedIn: 'root' })
export class SkillService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    // Picker lookup in catalogue order, open to every role
    listOptions(query: SkillOptionQuery): Observable<PagedResponse<SkillOptionResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.keyword) {
            params = params.set('keyword', query.keyword);
        }
        return this.http.get<PagedResponse<SkillOptionResponse>>(this.url(ApiEndpoint.SkillOptions), { params });
    }

    // Management list with usage counts (Admin/HR)
    search(query: SkillSearchQuery): Observable<PagedResponse<SkillResponse>> {
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
        return this.http.get<PagedResponse<SkillResponse>>(this.url(ApiEndpoint.Skill), { params });
    }

    create(body: SkillRequest): Observable<SkillResponse> {
        return this.http.post<SkillResponse>(this.url(ApiEndpoint.Skill), body);
    }

    update(id: string, body: SkillRequest): Observable<SkillResponse> {
        return this.http.put<SkillResponse>(this.url(`${ApiEndpoint.Skill}/${id}`), body);
    }

    // Drag and drop: the server renumbers the whole catalogue (Admin/HR)
    move(id: string, body: MoveSkillRequest): Observable<void> {
        return this.http.put<void>(this.url(`${ApiEndpoint.Skill}/${id}/position`), body);
    }
}