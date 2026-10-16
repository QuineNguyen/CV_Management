import { AuditLogSortField, SortDirection } from "../enums/sort-field.enum";
import { UserRole } from "../enums/user-role.enum";

/*
 * action and targetType stay plain strings: the server owns both lists and sends them through
 * the filter endpoints, so the client never keeps a copy that could drift.
 */
export interface AuditLogResponse {
    id: string;
    actorName: string | null;
    actorEmail: string | null;
    // Role at the moment of the action
    actorRole: UserRole;
    action: string;
    targetType: string;
    targetId: string;
    createdAt: string;
}

// Detail panel: the list row plus the raw values, usually JSON
export interface AuditLogDetailResponse extends AuditLogResponse {
    oldValue: string | null;
    newValue: string | null;
}

export interface AuditLogSearchQuery {
    actorId?: string;
    action?: string;
    targetType?: string;
    fromDate?: string;
    toDate?: string;
    sortBy?: AuditLogSortField;
    direction?: SortDirection;
    page: number;
    size: number;
}