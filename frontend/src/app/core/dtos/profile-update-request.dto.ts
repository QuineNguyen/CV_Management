import { ProfileUpdateStatus } from "../enums/profile-update-status.enum";
import { ProfileUpdateSortField, SortDirection } from "../enums/sort-field.enum";
import { UserRole } from "../enums/user-role.enum";

export interface ProfileUpdateSubmitRequest {
    fullName?: string | null;
    dateOfBirth?: string | null;
    phoneNumber?: string | null;
    address?: string | null;
    avatarImageId?: string | null;
}

export interface RejectProfileUpdateRequest {
    reason: string;
}

/*
 * Carries both sides of the change so the reviewer never opens a second screen. A requested_*
 * field that is null was not part of the proposal and renders as "unchanged".
 */
export interface ProfileUpdateRequestResponse {
    id: string;
    userId: string;
    userFullName: string | null;
    userEmail: string | null;
    status: ProfileUpdateStatus;

    currentFullName: string | null;
    currentDateOfBirth: string | null;
    currentPhoneNumber: string | null;
    currentAddress: string | null;
    currentAvatarImageId: string | null;
    currentAvatarUrl: string | null;

    requestedFullName: string | null;
    requestedDateOfBirth: string | null;
    requestedPhoneNumber: string | null;
    requestedAddress: string | null;
    requestedAvatarImageId: string | null;
    requestedAvatarUrl: string | null;

    reviewedByName: string | null;
    reviewedAt: string | null;
    rejectReason: string | null;

    createdAt: string;
}

export interface MyProfileResponse {
    id: string;
    fullName: string;
    email: string;
    username: string;
    role: UserRole;
    departmentCode: string | null;
    departmentName: string | null;
    dateOfBirth: string | null;
    phoneNumber: string | null;
    address: string | null;
    avatarImageId: string | null;
    avatarUrl: string | null;
    // False for an Admin: their edits apply immediately, so the page says Save, not Submit.
    requiresApproval: boolean;
    latestUpdateRequest: ProfileUpdateRequestResponse | null;
}

export interface ProfileUpdateRequestQuery {
    status?: ProfileUpdateStatus;
    sortBy?: ProfileUpdateSortField;
    direction?: SortDirection;
    page: number;
    size: number;
}

export interface PendingCountResponse {
    count: number;
}