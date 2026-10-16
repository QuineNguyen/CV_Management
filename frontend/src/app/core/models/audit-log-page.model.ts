import { AuditFieldChange } from "../enums/audit-log.enum";

export interface AuditLogPageState {
    index: number;
    size: number;
    total: number;
}

// Selected person in the actor filter
export interface AuditActorOption {
    id: string;
    fullName: string;
    email: string;
}

// One top-level field that differs between the old and new value
export interface AuditFieldDiff {
    key: string;
    change: AuditFieldChange;
}