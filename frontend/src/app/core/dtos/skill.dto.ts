import { SkillSortField, SortDirection } from "../enums/sort-field.enum";

// One row of the catalogue screen; cvCount uses the same scope as the CV search skill filter
export interface SkillResponse {
    id: string;
    code: string;
    name: string;
    displayOrder: number;
    createdByName: string | null;
    createdAt: string;
    cvCount: number;
}

// Lightweight entry for pickers and filters
export interface SkillOptionResponse {
    id: string;
    code: string;
    name: string;
}

// Body of create and update; there is no delete
export interface SkillRequest {
    code: string;
    name: string;
    displayOrder: number;
}

export interface SkillSearchQuery {
    keyword?: string;
    sortBy?: SkillSortField;
    direction?: SortDirection;
    page: number;
    size: number;
}

export interface SkillOptionQuery {
    keyword?: string;
    page: number;
    size: number;
}