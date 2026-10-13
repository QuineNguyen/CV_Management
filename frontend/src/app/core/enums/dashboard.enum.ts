export enum UrgencyLevel {
    Approaching = 'APPROACHING',
    LastDay = 'LAST_DAY',
    Overdue = 'OVERDUE',
}

export const URGENCY_LABELS: Record<UrgencyLevel, string> = {
    [UrgencyLevel.Approaching]: 'Due soon',
    [UrgencyLevel.LastDay]: 'Due today',
    [UrgencyLevel.Overdue]: 'Overdue',
};

// Query string handed to the full lists behind "View all"
export enum DashboardQueryParam {
    Filter = 'filter',
    Scope = 'scope',
}

export enum DashboardFilter {
    Overdue = 'overdue',
}

export enum DashboardScope {
    Team = 'team',
}

// Approval levels as stored on approval_assignments.level
export enum ApprovalLevel {
    TechLead = 1,
    Hr = 2,
}

export const APPROVAL_LEVEL_LABELS: Record<ApprovalLevel, string> = {
    [ApprovalLevel.TechLead]: 'Level 1 · Tech Lead',
    [ApprovalLevel.Hr]: 'Level 2 · HR',
};

// Material Symbols ligatures used on the dashboard
export enum DashboardIcon {
    Employees = 'group',
    Cvs = 'description',
    Published = 'verified',
    Unpublished = 'draft',
    Requests = 'assignment_late',
    ProfileRequests = 'manage_accounts',
    Review = 'rate_review',
    Queue = 'inbox',
    Team = 'groups',
    Overdue = 'alarm',
    Drafts = 'edit_note',
}