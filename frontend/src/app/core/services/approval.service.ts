import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { ApprovalQueueItem, ApprovalQueueQuery, DraftApproveResponse, DraftReviewResponse, DraftSubmitResponse } from "../dtos/approval.dto";
import { Observable } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { DraftRejectResponse, InlineCommentResponse, RejectDraftRequest, ReplyCommentRequest } from "../dtos/inline-comment.dto";

/*
 * The approval flow's client.
 *
 * No role check happens here or in the components that call it: the backend scopes the queue by
 * assignment and answers 403 on a draft that is not the caller's to review, so a guard in front of
 * these calls would only duplicate a rule it cannot enforce.
 */
@Injectable({ providedIn: 'root' })
export class ApprovalService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    getQueue(query: ApprovalQueueQuery): Observable<PagedResponse<ApprovalQueueItem>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }
        if (query.direction) {
            params = params.set('direction', query.direction);
        }
        return this.http.get<PagedResponse<ApprovalQueueItem>>(
            this.url(ApiEndpoint.ApprovalQueue), { params }
        );
    }

    // Locks the draft's content on success; the caller should navigate away rather than re-edit.
    submit(draftId: string): Observable<DraftSubmitResponse> {
        return this.http.post<DraftSubmitResponse>(
            this.url(`${ApiEndpoint.Approvals}/drafts/${draftId}/submit`), null
        );
    }

    resubmit(draftId: string): Observable<DraftSubmitResponse> {
        return this.http.post<DraftSubmitResponse>(
            this.url(`${ApiEndpoint.Approvals}/drafts/${draftId}/resubmit`), null
        );
    }

    openForReview(draftId: string): Observable<DraftReviewResponse> {
        return this.http.get<DraftReviewResponse>(
            this.url(`${ApiEndpoint.Approvals}/drafts/${draftId}/review`)
        );
    }

    approve(draftId: string): Observable<DraftApproveResponse> {
        return this.http.post<DraftApproveResponse>(
            this.url(`${ApiEndpoint.Approvals}/drafts/${draftId}/approve`), null
        );
    }

    reject(draftId: string, body: RejectDraftRequest): Observable<DraftRejectResponse> {
        return this.http.post<DraftRejectResponse>(
            this.url(`${ApiEndpoint.Approvals}/drafts/${draftId}/reject`), body
        );
    }

    replyToComment(commentId: string, body: ReplyCommentRequest): Observable<InlineCommentResponse> {
        return this.http.post<InlineCommentResponse>(
            this.url(`${ApiEndpoint.Approvals}/comments/${commentId}/reply`), body
        );
    }
}