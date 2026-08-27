import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { CreatedUserResponse, CreateUserRequest, DeactivateUserRequest, TechLeadOption, UpdateUserRequest, UserResponse, UserSearchQuery } from "../dtos/user.dto";
import { Observable } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

@Injectable({ providedIn: 'root' })
export class UserService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    search(query: UserSearchQuery): Observable<PagedResponse<UserResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.keyword) {
            params = params.set('keyword', query.keyword);
        }
        if (query.role) {
            params = params.set('role', query.role);
        }
        if (query.status) {
            params = params.set('status', query.status);
        }
        if (query.departmentId) {
            params = params.set('departmentId', query.departmentId);
        }
        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }
        if (query.direction) {
            params = params.set('direction', query.direction);
        }
        return this.http.get<PagedResponse<UserResponse>>(this.url(ApiEndpoint.User), { params },);
    }

    getById(id: string): Observable<UserResponse> {
        return this.http.get<UserResponse>(this.url(`${ApiEndpoint.User}/${id}`));
    }

    create(body: CreateUserRequest): Observable<CreatedUserResponse> {
        return this.http.post<CreatedUserResponse>(this.url(ApiEndpoint.User), body);
    }

    update(id: string, body: UpdateUserRequest): Observable<UserResponse> {
        return this.http.put<UserResponse>(this.url(`${ApiEndpoint.User}/${id}`), body);
    }

    deactivate(id: string, body: DeactivateUserRequest): Observable<void> {
        return this.http.post<void>(this.url(`${ApiEndpoint.User}/${id}/deactivate`), body);
    }

    activate(id: string): Observable<void> {
        return this.http.post<void>(this.url(`${ApiEndpoint.User}/${id}/activate`), null);
    }

    getTechLeads(): Observable<TechLeadOption[]> {
        return this.http.get<TechLeadOption[]>(this.url(ApiEndpoint.TechLead));
    }
}