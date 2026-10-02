import { HttpClient, HttpContext, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { BatchFailedItemResponse, BatchListQuery, BatchPageQuery, BatchPreviewQuery, BatchPreviewRequest, BatchPreviewResponse, BatchRequestResponse, CreateBatchRequest } from "../dtos/batch-request.dto";
import { Observable } from "rxjs";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { SKIP_ERROR_TOAST } from "../interceptors/skip-error-toast.token";
import { PagedResponse } from "../dtos/page.dto";

@Injectable({ providedIn: 'root' })
export class BatchRequestService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    // Recipients are recomputed on every call; both tables page independently
    preview(body: BatchPreviewRequest, query: BatchPreviewQuery): Observable<BatchPreviewResponse> {
        const params = new HttpParams()
            .set('includedPage', query.includedPage)
            .set('excludedPage', query.excludedPage)
            .set('size', query.size);
        return this.http.post<BatchPreviewResponse>(this.url(ApiEndpoint.UpdateRequestBatchPreview), body, { params });
    }

    create(body: CreateBatchRequest): Observable<BatchRequestResponse> {
        return this.http.post<BatchRequestResponse>(this.url(ApiEndpoint.UpdateRequestBatch), body);
    }

    // silent: a failed poll is retried on the next tick instead of toasting every few seconds
    getDetail(id: string, silent = false): Observable<BatchRequestResponse> {
        const context = new HttpContext().set(SKIP_ERROR_TOAST, silent);
        return this.http.get<BatchRequestResponse>(this.url(`${ApiEndpoint.BatchRequests}/${id}`), { context });
    }

    getFailedItems(id: string, query: BatchPageQuery): Observable<PagedResponse<BatchFailedItemResponse>> {
        const params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);
        return this.http.get<PagedResponse<BatchFailedItemResponse>>(
            this.url(`${ApiEndpoint.BatchRequests}/${id}${ApiEndpoint.BatchFailedItems}`), { params },
        );
    }

    resendFailed(id: string): Observable<BatchRequestResponse> {
        return this.http.post<BatchRequestResponse>(
            this.url(`${ApiEndpoint.BatchRequests}/${id}${ApiEndpoint.BatchResendFailed}`), null,
        );
    }

    // silent: background refresh while a batch on the page is still processing
    list(query: BatchListQuery, silent = false): Observable<PagedResponse<BatchRequestResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);
        if (query.status) {
            params = params.set('status', query.status);
        }
        const context = new HttpContext().set(SKIP_ERROR_TOAST, silent);
        return this.http.get<PagedResponse<BatchRequestResponse>>(this.url(ApiEndpoint.BatchRequests), { params, context });
    }
}