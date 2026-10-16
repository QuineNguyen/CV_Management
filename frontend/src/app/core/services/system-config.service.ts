import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { Observable } from "rxjs";
import { SystemConfigResponse, UpdateSystemConfigRequest } from "../dtos/system-config.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

// Reminder schedule and approval SLA (Admin)
@Injectable({ providedIn: 'root' })
export class SystemConfigService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    get(): Observable<SystemConfigResponse> {
        return this.http.get<SystemConfigResponse>(this.url(ApiEndpoint.SystemConfig));
    }

    update(body: UpdateSystemConfigRequest): Observable<SystemConfigResponse> {
        return this.http.put<SystemConfigResponse>(this.url(ApiEndpoint.SystemConfig), body);
    }
}