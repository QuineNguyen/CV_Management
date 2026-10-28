import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { map, Observable } from "rxjs";
import { CvCreateRequest, CvDeleteRequest, CvDetailResponse, CvEditRequest, CvEditResponse, CvPdfFile, CvPdfLinkResponse, CvResponse, CvSearchFiltersResponse, CvSearchItemResponse, CvSearchQuery, CvVersionSummary, DeletedCvQuery } from "../dtos/cv.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { PagedResponse } from "../dtos/page.dto";
import { PendingNoteResponse } from "../dtos/update-request.dto";
import { CvRollbackRequest, CvVersionHistoryItem, CvVersionQuery, VersionDiffResponse } from "../dtos/cv-version.dto";
import { CvPdfLink } from "../models/cv-pdf.model";
import { CvContent } from "../models/cv-content.model";

@Injectable({ providedIn: 'root' })
export class CvService {

    private static readonly MS_PER_SECOND = 1000;

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    listByProfile(profileId: string, includeDeleted = false): Observable<CvResponse[]> {
        const params = new HttpParams().set('includeDeleted', includeDeleted);
        return this.http.get<CvResponse[]>(this.url(`${ApiEndpoint.Profiles}/${profileId}/cvs`), { params });
    }

    // What a new CV of the profile starts from: the master's structure or the blank template
    getScaffold(profileId: string): Observable<CvContent> {
        return this.http.get<CvContent>(this.url(`${ApiEndpoint.Profiles}/${profileId}/cvs/scaffold`));
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

    listVersions(id: string, query: CvVersionQuery): Observable<PagedResponse<CvVersionHistoryItem>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }
        if (query.direction) {
            params = params.set('direction', query.direction);
        }
        return this.http.get<PagedResponse<CvVersionHistoryItem>>(
            this.url(`${ApiEndpoint.Cvs}/${id}/versions`), { params }
        );
    }

    // Without fromId the version is compared with an empty CV
    diffVersions(id: string, toId: string, fromId: string | null): Observable<VersionDiffResponse> {
        let params = new HttpParams().set('to', toId);
        if (fromId) {
            params = params.set('from', fromId);
        }
        return this.http.get<VersionDiffResponse>(
            this.url(`${ApiEndpoint.Cvs}/${id}/versions/diff`), { params }
        );
    }

    /*
     * A short-lived signed link to one published version. The preview iframe and a new tab open
     * it directly - neither can send the JWT header - and its path ends with the file name, so
     * the browser's PDF viewer saves the file under that name instead of a random id.
     */
    createPdfLink(id: string, versionId: string): Observable<CvPdfLink> {
        return this.http.post<CvPdfLinkResponse>(
            this.url(`${ApiEndpoint.Cvs}/${id}/versions/${versionId}/pdf-link`), null
        ).pipe(
            map(response => ({
                url: this.url(response.path),
                fileName: response.fileName,
                expiresAt: Date.now() + response.expiresInSeconds * CvService.MS_PER_SECOND,
            })),
        );
    }

    // Admin/HR: publishes a new version with the content of an older one
    rollback(id: string, body: CvRollbackRequest): Observable<CvVersionHistoryItem> {
        return this.http.post<CvVersionHistoryItem>(
            this.url(`${ApiEndpoint.Cvs}/${id}/rollback`), body
        );
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

    // Feedback notes of the pending update request, flat per anchor
    getPendingNotes(id: string): Observable<PendingNoteResponse[]> {
        return this.http.get<PendingNoteResponse[]>(this.url(`${ApiEndpoint.Cvs}/${id}/pending-notes`))
    }

    // Scope and filtering happen on the server; the page shows what comes back as-is
    search(query: CvSearchQuery): Observable<PagedResponse<CvSearchItemResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.employeeName) {
            params = params.set('employeeName', query.employeeName);
        }
        if (query.profileName) {
            params = params.set('profileName', query.profileName);
        }
        if (query.departmentId) {
            params = params.set('departmentId', query.departmentId);
        }
        if (query.teamId) {
            params = params.set('teamId', query.teamId);
        }
        if (query.language) {
            params = params.set('language', query.language);
        }
        if (query.allProfiles) {
            params = params.set('allProfiles', true);
        }
        if (query.keyword) {
            params = params.set('keyword', query.keyword);
        }
        // Repeated key: skillIds=a&skillIds=b
        for (const skillId of query.skillIds ?? []) {
            params = params.append('skillIds', skillId);
        }
        if (query.sortBy) {
            params = params.set('sortBy', query.sortBy);
        }
        if (query.direction) {
            params = params.set('direction', query.direction);
        }
        return this.http.get<PagedResponse<CvSearchItemResponse>>(this.url(ApiEndpoint.Cvs), { params });
    }

    getFilterOptions(): Observable<CvSearchFiltersResponse>{
        return this.http.get<CvSearchFiltersResponse>(this.url(ApiEndpoint.CvSearchFilters));
    }
}