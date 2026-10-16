import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { AuditLogDetailResponse, AuditLogResponse, AuditLogSearchQuery } from "../dtos/audit-log.dto";
import { Observable } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

// Read-only audit trail (Admin)
@Injectable({ providedIn: 'root' })
export class AuditLogService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    search(query: AuditLogSearchQuery): Observable<PagedResponse<AuditLogResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.actorId) {
            params = params.set('actorId', query.actorId);
        }
        if (query.action) {
            params = params.set('action', query.action);
        }
        if (query.targetType) {
            params = params.set('targetType', query.targetType);
        }
        if (query.fromDate) {
            params = params.set('fromDate', query.fromDate);
        }
        if (query.toDate) {
            params = params.set('toDate', query.toDate);
        }
        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }
        if (query.direction) {
            params = params.set('direction', query.direction);
        }
        return this.http.get<PagedResponse<AuditLogResponse>>(this.url(ApiEndpoint.AuditLog), { params });
    }

    getById(id: string): Observable<AuditLogDetailResponse> {
        return this.http.get<AuditLogDetailResponse>(this.url(`${ApiEndpoint.AuditLog}/${id}`));
    }

    listActions(): Observable<string[]> {
        return this.http.get<string[]>(this.url(ApiEndpoint.AuditLogActions));
    }

    listTargetTypes(): Observable<string[]> {
        return this.http.get<string[]>(this.url(ApiEndpoint.AuditLogTargetTypes));
    }
}