import { AccountStatus } from "../enums/account-status.enum";
import { SortDirection, TeamSortField } from "../enums/sort-field.enum";
import { UserRole } from "../enums/user-role.enum";
import { PageQuery } from "./page.dto";

export interface TeamResponse {
    id: string;
    code: string;
    name: string;
    description: string | null;
    techLeadId: string;
    techLeadFullName: string | null;
    displayOrder: number;
    memberCount: number;
}

export interface TeamRequest {
    code: string;
    name: string;
    description: string | null;
    techLeadId: string;
}

export interface TeamMemberResponse {
    // team_members.id, not the user id.
    id: string;
    userId: string;
    fullName: string;
    email: string;
    username: string;
    role: UserRole;
    status: AccountStatus;
    primaryTeam: boolean;
}

// Query parameters for GET /teams.
export interface TeamSearchQuery extends PageQuery {
    keyword?: string | null;
    sortBy?: TeamSortField;
    direction?: SortDirection;
}