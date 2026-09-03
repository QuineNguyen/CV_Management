import { HttpInterceptorFn, HttpResponse } from "@angular/common/http";
import { environment } from "../../../environments/environment";
import { ApiResponse } from "../dtos/api-response.dto";
import { map } from "rxjs";

/*
 * Strips the { code, message, data } envelope from successful responses.
 *
 * - Doing it here rather than in each service keeps every service signature describing the
 * payload it actually delivers and means the envelope is a transport concern rather than
 * something 30 call sites have to know about.
 * 
 * - Failure are untouched: an error body never reaches this operator, so errorInceptor
 * reads the raw envelope and gets `code` straight off it.
 */
export const unwrapInterceptor: HttpInterceptorFn = (req, next) => {
    if (!req.url.startsWith(environment.apiBaseUrl)) {
        return next(req);
    }

    return next(req).pipe(
        map(event => {
            if (event instanceof HttpResponse && isEnvelope(event.body)) {
                return event.clone({ body: event.body.data });
            }
            return event;
        }),
    );
};

// Blob and text responses are not envelopes; unwrapping them would null out the payload.
const isEnvelope = (body: unknown): body is ApiResponse<unknown> =>
    !!body && typeof body === 'object' && 'code' in body && 'data' in body;