import { PageQuery } from "./page.dto";

export interface CvProfileResponse {
    id: string;
    employeeId: string;
    name: string;
    description: string | null;
    primary: boolean;
    linkedTeamId: string;
    linkedTeamCode: string | null;
    linkedTeamName: string | null;
    cvCount: number;
    createdAt: string;
    updatedAt: string;
}

export interface CvProfileRequest {
    name: string;
    description: string | null;
    linkedTeamId: string;
}

export interface CvProfileQuery extends PageQuery {
    employeeId: string;
}