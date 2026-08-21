import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import { DepartmentNode, DepartmentRequest, ReorderRequest } from "../dtos/department.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

@Injectable({ providedIn: 'root' })
export class DepartmentService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    getTree(): Observable<DepartmentNode[]> {
        return this.http.get<DepartmentNode[]>(this.url(ApiEndpoint.DepartmentTree));
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

    reorder(parentId: string | null, body: ReorderRequest): Observable<void> {
        const endpoint = parentId === null
            ? ApiEndpoint.DepartmentRootReorder
            : `${ApiEndpoint.Departments}/${parentId}/reorder`;
        return this.http.post<void>(this.url(endpoint), body);
    }
}