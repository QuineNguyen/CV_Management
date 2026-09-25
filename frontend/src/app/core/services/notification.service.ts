import { isPlatformBrowser } from "@angular/common";
import { HttpClient, HttpContext, HttpParams } from "@angular/common/http";
import { inject, Injectable, PLATFORM_ID, signal } from "@angular/core";
import { NotificationQuery, NotificationResponse, UnreadCountResponse } from "../dtos/notification.dto";
import { environment } from "../../../environments/environment";
import { catchError, EMPTY, filter, fromEvent, merge, Observable, switchMap, tap, timer } from "rxjs";
import { PagedResponse } from "../dtos/page.dto";
import { ApiEndpoint } from "../enums/api-endpoint.enum";
import { SKIP_ERROR_TOAST } from "../interceptors/skip-error-toast.token";

/*
 * Client of the in-app feed and owner of the badge state.
 * - The unread count is one signal shared by the shell and the notifications page.
 * - Every write returns the server's count; the badge is set from it rather than decremented
 * locally, so two tabs cannot drift apart for long.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {

    private static readonly POLL_INTERVAL_MS = 60_000;
    private static readonly RECENT_SIZE = 5;

    private readonly http = inject(HttpClient);
    private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

    private readonly unreadCountSignal = signal(0);
    private readonly recentSignal = signal<NotificationResponse[]>([]);
    private readonly recentLoadingSignal = signal(false);

    readonly unreadCount = this.unreadCountSignal.asReadonly();
    readonly recent = this.recentSignal.asReadonly();
    readonly recentLoading = this.recentLoadingSignal.asReadonly();

    private url(endpoint: string): string {
        return `${environment.apiBaseUrl}${endpoint}`;
    }

    list(query: NotificationQuery, silent = false): Observable<PagedResponse<NotificationResponse>> {
        let params = new HttpParams()
            .set('page', query.page)
            .set('size', query.size);

        if (query.unreadOnly) {
            params = params.set('unreadOnly', true);
        }
        return this.http.get<PagedResponse<NotificationResponse>>(
            this.url(ApiEndpoint.Notifications), { params, context: this.context(silent) }
        );
    }

    // Background call: a failed refresh never raises a toast.
    fetchUnreadCount(): Observable<UnreadCountResponse> {
        return this.http.get<UnreadCountResponse>(
            this.url(ApiEndpoint.NotificationsUnreadCount), { context: this.context(true) }
        ).pipe(tap(result => this.unreadCountSignal.set(result.count)));
    }

    refreshUnreadCount(): void {
        this.fetchUnreadCount().pipe(catchError(() => EMPTY)).subscribe();
    }

    // Latest items for the bell menu, loaded each time it opens.
    refreshRecent(): void {
        this.recentLoadingSignal.set(true);
        this.list({ page: 0, size: NotificationService.RECENT_SIZE }, true).subscribe({
            next: page => {
                this.recentSignal.set(page.content);
                this.recentLoadingSignal.set(false);
            },
            error: () => this.recentLoadingSignal.set(false),
        });
    }

    markAsRead(id: string): Observable<UnreadCountResponse> {
        return this.http.patch<UnreadCountResponse>(
            this.url(`${ApiEndpoint.Notifications}/${id}/read`), null
        ).pipe(tap(result => {
            this.unreadCountSignal.set(result.count);
            this.recentSignal.update(items =>
                items.map(item => item.id === id ? { ...item, read: true } : item));
        }));
    }

    markAllAsRead(): Observable<UnreadCountResponse> {
        return this.http.patch<UnreadCountResponse>(
            this.url(ApiEndpoint.NotificationsReadAll), null
        ).pipe(tap(result => {
            this.unreadCountSignal.set(result.count);
            this.recentSignal.update(items => items.map(item => ({ ...item, read: true })));
        }));
    }

    /*
     * Unread count every minute while the tab is visible, plus once when it becomes visible again.
     * Browser only: on the server a timer would keep the render from ever finishing.
     */
    poll(): Observable<UnreadCountResponse> {
        if (!this.isBrowser) {
            return EMPTY;
        }
        return merge(
            timer(0, NotificationService.POLL_INTERVAL_MS),
            fromEvent(document, 'visibilitychange'),
        ).pipe(
            filter(() => document.visibilityState === 'visible'),
            // A slow response is dropped when the next tick arrives instead of piling up.
            switchMap(() => this.fetchUnreadCount().pipe(catchError(() => EMPTY))),
        );
    }

    // Called on sign-out so the next user never sees the previous one's items.
    reset(): void {
        this.unreadCountSignal.set(0);
        this.recentSignal.set([]);
    }

    private context(silent: boolean): HttpContext {
        return new HttpContext().set(SKIP_ERROR_TOAST, silent);
    }
}