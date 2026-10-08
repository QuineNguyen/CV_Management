import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { CreateSingleUpdateRequest, CreateUpdateRequestResponse, UpdateRequestQuery, UpdateRequestResponse } from "../dtos/update-request.dto";
import { Observable } from "rxjs";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { PagedResponse } from "../dtos/page.dto";

@Injectable({ providedIn: 'root' })
export class UpdateRequestService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    // ALL comes back as up to three rows plus the languages that were skipped
    create(body: CreateSingleUpdateRequest): Observable<CreateUpdateRequestResponse> {
        return this.http.post<CreateUpdateRequestResponse>(this.url(ApiEndpoint.UpdateRequests), body);
    }

    // Rows are already narrowed to the caller's scope by the server
    list(query: UpdateRequestQuery): Observable<PagedResponse<UpdateRequestResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        const optional: Record<string, string | undefined> = {
            status: query.status,
            departmentId: query.departmentId,
            language: query.language,
            fromDate: query.fromDate,
            toDate: query.toDate,
            sortBy: query.sortBy,
            direction: query.direction,
        };
        for (const [key, value] of Object.entries(optional)) {
            if (value) {
                params = params.set(key, value);
            }
        }

        if (query.batchId) {
            params = params.set('batchId', query.batchId);
        }

        return this.http.get<PagedResponse<UpdateRequestResponse>>(this.url(ApiEndpoint.UpdateRequests), { params });
    }

    // PENDING only; Admin any request, HR their own. 409 when someone else closed it first
    cancel(id: string): Observable<UpdateRequestResponse> {
        return this.http.post<UpdateRequestResponse>(this.url(`${ApiEndpoint.UpdateRequests}/${id}/cancel`), null);
    }
}