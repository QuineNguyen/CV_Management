import { NotificationEventType } from "../enums/notification-event-type.enum";

export interface NotificationResponse {
    id: string;
    type: NotificationEventType;
    content: string;
    // Angular route relative to the app root, e.g. "cvs/{id}"
    link: string;
    read: boolean;
    createdAt: string;
}

export interface UnreadCountResponse {
    count: number;
}

export interface NotificationQuery {
    page: number;
    size: number;
    unreadOnly?: boolean;
}