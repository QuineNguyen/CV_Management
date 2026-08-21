export interface DepartmentNode {
    id: string;
    code: string;
    name: string;
    parentDepartmentId: string | null;
    displayOrder: number;
    children: DepartmentNode[];
}

export interface DepartmentRequest {
    code: string;
    name: string;
    parentDepartmentId: string | null;
}

export interface MoveDepartmentRequest {
    parentDepartmentId: string | null;
    afterDepartmentId: string | null;
    beforeDepartmentId: string | null;
}

export interface DepartmentSearchQuery {
    keyword?: string;
    excludeSubtreeOf?: string;
    page: number;
    size: number;
}