import { DepartmentNode } from "../dtos/department.dto";
import { DialogMode } from "../enums/dialog-mode.enum";

export interface DepartmentDialogState {
    mode: DialogMode;
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