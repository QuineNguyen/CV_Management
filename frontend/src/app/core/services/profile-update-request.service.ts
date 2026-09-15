import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable, signal } from "@angular/core";
import { environment } from "../../../environments/environment";
import { PendingCountResponse, ProfileUpdateRequestQuery, ProfileUpdateRequestResponse } from "../dtos/profile-update-request.dto";
import { catchError, EMPTY, Observable, tap } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

/*
 * The reviewer half. Every method here is already scoped server-side by the requester's role, so
 * nothing in this file filters anything - an HR simply receives fewer rows.
 */
@Injectable({ providedIn: 'root' })
export class ProfileUpdateRequestService {

    private readonly http = inject(HttpClient);

    private readonly pendingCountSignal = signal(0);
    readonly pendingCount = this.pendingCountSignal.asReadonly();

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    list(query: ProfileUpdateRequestQuery): Observable<PagedResponse<ProfileUpdateRequestResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.status) {
            params = params.set('status', query.status);
        }
        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }
        if (query.direction) {
            params = params.set('direction', query.direction);
        }
        return this.http.get<PagedResponse<ProfileUpdateRequestResponse>>(
            this.url(ApiEndpoint.ProfileUpdateRequests), { params }
        );
    }

    getById(id: string): Observable<ProfileUpdateRequestResponse> {
        return this.http.get<ProfileUpdateRequestResponse>(
            this.url(`${ApiEndpoint.ProfileUpdateRequests}/${id}`)
        );
    }

    approve(id: string): Observable<ProfileUpdateRequestResponse> {
        return this.http.post<ProfileUpdateRequestResponse>(
            this.url(`${ApiEndpoint.ProfileUpdateRequests}/${id}/approve`), null
        ).pipe(tap(() => this.refreshPendingCount()));
    }

    reject(id: string, reason: string): Observable<ProfileUpdateRequestResponse> {
        return this.http.post<ProfileUpdateRequestResponse>(
            this.url(`${ApiEndpoint.ProfileUpdateRequests}/${id}/reject`), { reason }
        ).pipe(tap(() => this.refreshPendingCount()));
    }

    /*
     * Fire and forget: the badge is an aid, so a failure here must never surface as an error on
     * whatever screen happened to trigger it.
     */
    refreshPendingCount(): void {
        this.http.get<PendingCountResponse>(
            this.url(`${ApiEndpoint.ProfileUpdateRequests}/pending-count`),
        ).pipe(
            catchError(() => EMPTY),
        ).subscribe(result => this.pendingCountSignal.set(result.count));
    }
}