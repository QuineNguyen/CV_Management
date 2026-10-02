import { PageQuery } from "./page.dto";

export interface DepartmentNode {
    id: string;
    code: string;
    name: string;
    parentDepartmentId: string | null;
    displayOrder: number;
    updatedAt: string;
    children: DepartmentNode[];
}

export interface DepartmentRequest {
    code: string;
    name: string;
    parentDepartmentId: string | null;
    updatedAt?: string;
}

export interface MoveDepartmentRequest {
    parentDepartmentId: string | null;
    afterDepartmentId: string | null;
    beforeDepartmentId: string | null;
    updatedAt?: string;
}

export interface DepartmentSearchQuery extends PageQuery {
    keyword?: string;
    excludeSubtreeOf?: string;
}