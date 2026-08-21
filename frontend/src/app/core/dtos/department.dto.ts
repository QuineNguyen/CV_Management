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

export interface ReorderRequest {
    orderedIds: string[];
}