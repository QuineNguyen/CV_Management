import { AccountStatus } from "../enums/account-status.enum";
import { SortDirection, UserSortField } from "../enums/sort-field.enum";
import { UserRole } from "../enums/user-role.enum";
import { PageQuery } from "./page.dto";
import { TeamResponse } from "./team.dto";

export interface UserTeamInfo {
    teamId: string;
    teamCode: string;
    teamName: string;
    primary: boolean;
}

export interface UserResponse {
    id: string;
    fullName: string;
    email: string;
    username: string;
    role: UserRole;
    status: AccountStatus;
    primaryDepartmentId: string;
    departmentCode: string | null;
    departmentName: string | null;
    dateOfBirth: string | null;
    phoneNumber: string | null;
    address: string | null;
    avatarImageId: string | null;
    teams: UserTeamInfo[];
    // Teams this user currently leads. Drives the deactivate dialog.
    ledTeams: TeamResponse[];
    mustChangePassword: boolean;
    createdAt: string;
}

// One team membership sent when creating or updating a user.
export interface TeamAssignment {
    teamId: string;
    primary: boolean;
}

export interface CreateUserRequest {
    fullName: string;
    email: string;
    username: string;
    role: UserRole;
    primaryDepartmentId: string;
    dateOfBirth: string | null;
    phoneNumber: string | null;
    address: string | null;
    teams: TeamAssignment[];
}

// Email and username are absent because they are immutable after creation.
export interface UpdateUserRequest {
    fullName: string;
    role: UserRole;
    primaryDepartmentId: string;
    dateOfBirth: string | null;
    phoneNumber: string | null;
    address: string | null;
    teams: TeamAssignment[];
}

export interface TeamReplacement {
    teamId: string;
    replacementTechLeadId: string;
}

export interface DeactivateUserRequest {
    replacements: TeamReplacement[];
}

// Returned once on creation so the admin can hand over the temporary password.
export interface CreatedUserResponse {
    user: UserResponse;
    temporaryPassword: string;
}

export interface TechLeadOption {
    id: string;
    fullName: string;
    email: string;
    username: string;
}

// Query parameters for GET /users.
export interface UserSearchQuery extends PageQuery {
    keyword?: string;
    role?: UserRole;
    status?: AccountStatus;
    departmentId?: string;
    sortBy?: UserSortField;
    direction?: SortDirection;
}