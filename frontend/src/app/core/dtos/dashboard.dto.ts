import { CvLanguage } from "../enums/cv-language.enum";
import { UrgencyLevel } from "../enums/dashboard.enum";

// ---------- Shared widgets ----------

export interface OverdueRequestSummary {
    requestId: string;
    // Null while the request still waits for its CV
    cvId: string | null;
    employeeName: string | null;
    // Null when the request targets no profile yet
    profileName: string | null;
    language: CvLanguage;
    deadline: string;
    // Positive = days past the deadline day, 0 = due today, negative = days left
    daysOverdue: number;
    urgency: UrgencyLevel;
}

export interface RequestDeadlineWidget {
    approachingDays: number;
    approachingCount: number;
    lastDayCount: number;
    overdueCount: number;
    items: OverdueRequestSummary[];
}

export interface ApprovalOverdueSummary {
    assignmentId: string;
    cvOwnerName: string | null;
    assigneeName: string | null;
    level: number;
    dueAt: string;
    // Whole days past the SLA; 0 means less than a day
    daysOverdue: number;
}

export interface ApprovalSlaWidget {
    overdueCount: number;
    items: ApprovalOverdueSummary[];
}

// ---------- Per-role responses ----------

export interface AdminDashboardResponse {
    totalEmployees: number;
    totalActiveCvs: number;
    cvsWithVersion: number;
    cvsWithoutVersion: number;
    pendingUpdateRequestCount: number;
    pendingProfileUpdateCount: number;
    draftsUnderReviewCount: number;
    myPendingApprovalCount: number;
    requestDeadlines: RequestDeadlineWidget;
    approvalSla: ApprovalSlaWidget;
    generatedAt: string;
}

export interface TechLeadDashboardResponse {
    teamMemberCount: number;
    teamCvCount: number;
    teamPendingRequestCount: number;
    myPendingApprovalCount: number;
    myOverdueApprovalCount: number;
    requestDeadlines: RequestDeadlineWidget;
    generatedAt: string;
}

export interface EmployeeDashboardResponse {
    myCvCount: number;
    openDraftCount: number;
    draftsInReviewCount: number;
    pendingRequestCount: number;
    requestDeadlines: RequestDeadlineWidget;
    generatedAt: string;
}