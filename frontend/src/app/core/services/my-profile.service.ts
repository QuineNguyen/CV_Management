import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { Observable } from "rxjs";
import { MyProfileResponse, ProfileUpdateSubmitRequest } from "../dtos/profile-update-request.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

/*
 * The self-service half of the workflow. save() covers both outcomes on purpose: the server
 * decides from the caller's role whether the values are written straight away or queued for
 * review and returns the resulting profile either way.
 */
@Injectable({ providedIn: 'root' })
export class MyProfileService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    getMyProfile(): Observable<MyProfileResponse> {
        return this.http.get<MyProfileResponse>(this.url(ApiEndpoint.MyProfile));
    }

    save(body: ProfileUpdateSubmitRequest): Observable<MyProfileResponse> {
        return this.http.put<MyProfileResponse>(this.url(ApiEndpoint.MyProfile), body);
    }

    // Withdrawal returns no payload; the caller reloads the profile to refresh the banner.
    withdrawPendingRequest(): Observable<void> {
        return this.http.delete<void>(this.url(ApiEndpoint.MyProfileUpdateRequest));
    }
}