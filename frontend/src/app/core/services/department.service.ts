import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import { DepartmentNode, DepartmentRequest, DepartmentSearchQuery, MoveDepartmentRequest } from "../dtos/department.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { PagedResponse, PageQuery } from "../dtos/page.dto";

@Injectable({ providedIn: 'root' })
export class DepartmentService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    getTree(query: PageQuery): Observable<PagedResponse<DepartmentNode>> {
        const params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);
        return this.http.get<PagedResponse<DepartmentNode>>(this.url(ApiEndpoint.DepartmentTree), { params });
    }

    search(query: DepartmentSearchQuery): Observable<PagedResponse<DepartmentNode>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.keyword) {
            params = params.set('keyword', query.keyword);
        }
        if (query.excludeSubtreeOf) {
            params = params.set('excludeSubtreeOf', query.excludeSubtreeOf);
        }

        return this.http.get<PagedResponse<DepartmentNode>>(this.url(ApiEndpoint.Departments), { params });
    }

    getById(id: string): Observable<DepartmentNode> {
        return this.http.get<DepartmentNode>(this.url(`${ApiEndpoint.Departments}/${id}`));
    }

    create(body: DepartmentRequest): Observable<DepartmentNode> {
        return this.http.post<DepartmentNode>(this.url(ApiEndpoint.Departments), body);
    }

    update(id: string, body: DepartmentRequest): Observable<DepartmentNode> {
        return this.http.put<DepartmentNode>(this.url(`${ApiEndpoint.Departments}/${id}`), body);
    }

    delete(id: string): Observable<void> {
        return this.http.delete<void>(this.url(`${ApiEndpoint.Departments}/${id}`));
    }

    move(id: string, body: MoveDepartmentRequest): Observable<void> {
        return this.http.put<void>(this.url(`${ApiEndpoint.Departments}/${id}/move`), body);
    }
}