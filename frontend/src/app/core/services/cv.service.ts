import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { Observable } from "rxjs";
import { CvCreateRequest, CvDeleteRequest, CvDetailResponse, CvEditRequest, CvEditResponse, CvResponse, CvVersionSummary, DeletedCvQuery } from "../dtos/cv.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { PagedResponse } from "../dtos/page.dto";

@Injectable({ providedIn: 'root' })
export class CvService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    listByProfile(profileId: string, includeDeleted = false): Observable<CvResponse[]> {
        const params = new HttpParams().set('includeDeleted', includeDeleted);
        return this.http.get<CvResponse[]>(this.url(`${ApiEndpoint.Profiles}/${profileId}/cvs`), { params });
    }

    listDeleted(query: DeletedCvQuery): Observable<PagedResponse<CvResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }
        if (query.direction) {
            params = params.set('direction', query.direction);
        }
        return this.http.get<PagedResponse<CvResponse>>(
            this.url(`${ApiEndpoint.Cvs}/deleted`), { params }
        );
    }
    
    getById(id: string): Observable<CvDetailResponse> {
        return this.http.get<CvDetailResponse>(this.url(`${ApiEndpoint.Cvs}/${id}`));
    }

    listVersions(id: string): Observable<CvVersionSummary[]> {
        return this.http.get<CvVersionSummary[]>(this.url(`${ApiEndpoint.Cvs}/${id}/versions`));
    }

    create(profileId: string, body: CvCreateRequest): Observable<CvResponse> {
        return this.http.post<CvResponse>(this.url(`${ApiEndpoint.Profiles}/${profileId}/cvs`), body);
    }

    edit(id: string, body: CvEditRequest): Observable<CvEditResponse> {
        return this.http.put<CvEditResponse>(this.url(`${ApiEndpoint.Cvs}/${id}/content`), body);
    }

    // A body is sent only when a successor master must be named; the server rejects a missing one.
    delete(id: string, body?: CvDeleteRequest): Observable<void> {
        return this.http.delete<void>(this.url(`${ApiEndpoint.Cvs}/${id}`), { body: body ?? {} });
    }

    restore(id: string): Observable<CvResponse> {
        return this.http.post<CvResponse>(this.url(`${ApiEndpoint.Cvs}/${id}/restore`), null);
    }
}