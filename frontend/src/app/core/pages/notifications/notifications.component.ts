import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { NotificationService } from "../../services/notification.service";
import { Router } from "@angular/router";
import { ToastService } from "../../services/toast.service";
import { NOTIFICATION_TYPE_ICONS, NOTIFICATION_TYPE_LABELS, NotificationFilter } from "../../enums/notification-event-type.enum";
import { NotificationResponse } from "../../dtos/notification.dto";
import { NotificationPageState } from "../../models/notification.model";
import { relativeTimeOf } from "../../utils/relative-time.util";

/*
 * Full feed of the signed-in user. The server returns only the caller's rows, so nothing is
 * filtered here beyond the read/unread switch the server applies too.
 */
@Component({
    selector: 'app-notifications',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, DatePipe],
    templateUrl: './notifications.component.html',
    styleUrl: './notifications.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 10;

    private readonly notificationService = inject(NotificationService);
    private readonly router = inject(Router);
    private readonly toast = inject(ToastService);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly NotificationFilter = NotificationFilter;
    readonly typeLabels = NOTIFICATION_TYPE_LABELS;
    readonly typeIcons =  NOTIFICATION_TYPE_ICONS;

    readonly unreadCount = this.notificationService.unreadCount;
    readonly filter = signal(NotificationFilter.All);
    readonly items = signal<NotificationResponse[]>([]);
    readonly loading = signal(false);
    readonly markingAll = signal(false);

    readonly pageState = signal<NotificationPageState>({
        index: 0,
        size: NotificationsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly isEmpty = computed(() => !this.loading() && this.items().length === 0);

    ngOnInit(): void {
        this.refresh();
    }

    // ---------- Loading ----------

    refresh(): void {
        this.load();
        this.notificationService.refreshUnreadCount();
    }

    load(): void {
        this.loading.set(true);
        const { index, size } = this.pageState();

        this.notificationService.list({
            page: index,
            size,
            unreadOnly: this.filter() === NotificationFilter.Unread,
        }).subscribe({
            next: result => {
                this.items.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.loading.set(false);
            },
            // The interceptor already surfaced the reason; the list keeps its previous contents.
            error: () => this.loading.set(false),
        });
    }

    selectFilter(filter: NotificationFilter): void {
        if (filter === this.filter()) {
            return;
        }
        this.filter.set(filter);
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.load();
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.load();
    }

    // ---------- Actions ----------

    // Opening an item is also reading it; navigation does not wait for the write.
    open(item: NotificationResponse): void {
        if (!item.read) {
            this.markRead(item);
        }
        void this.router.navigateByUrl('/' + item.link);
    }

    markRead(item: NotificationResponse, event?: Event): void {
        event?.stopPropagation();
        this.notificationService.markAsRead(item.id).subscribe({
            next: () => this.items.update(items =>
                items.map(current => current.id === item.id ? { ...current, read: true } : current)
            ),
        });
    }

    markAllRead(): void {
        this.markingAll.set(true);
        this.notificationService.markAllAsRead().subscribe({
            next: () => {
                this.items.update(items => items.map(item => ({ ...item, read: true })));
                this.markingAll.set(false);
                this.toast.success('All notifications marked as read');
            },
            error: () => this.markingAll.set(false),
        });
    }

    // ---------- Presentation ----------

    relativeTime(item: NotificationResponse): string {
        return relativeTimeOf(item.createdAt);
    }
}