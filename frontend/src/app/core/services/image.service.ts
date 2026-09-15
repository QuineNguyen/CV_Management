import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../environments/environment";
import { Observable } from "rxjs";
import { ImageUploadResponse } from "../dtos/image.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";

/*
 * Images are uploaded on their own, before whatever will reference them is saved. That is what
 * lets the crop dialog show a preview without touching a profile or a CV draft and why an image
 * nobody ends up pointing at is simply left behind.
 */
@Injectable({ providedIn: 'root' })
export class ImageService {

    private readonly http = inject(HttpClient);

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    /*
     * No Content-Type header: the browser sets multipart/form-data with the boundary itself and
     * setting it by hand produces a request the server cannot parse.
     */
    upload(blob: Blob, filename = 'avatar.jpg'): Observable<ImageUploadResponse> {
        const form = new FormData();
        form.append('file', blob, filename);
        return this.http.post<ImageUploadResponse>(this.url(ApiEndpoint.Images), form);
    }
}