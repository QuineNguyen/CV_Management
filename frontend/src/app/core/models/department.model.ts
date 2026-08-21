import { DepartmentNode } from "../dtos/department.dto";
import { DepartmentDialogMode } from "../enums/department-dialog-mode.enum";

export interface DepartmentDialogState {
    mode: DepartmentDialogMode;
    department: DepartmentNode | null;
    parentId: string | null;
}

export interface DepartmentDropList {
    parentId: string | null;
    nodes: DepartmentNode[];
}

export interface DepartmentPageState {
    index: number;
    size: number;
    total: number;
}