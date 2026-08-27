import { SortDirection } from "../enums/sort-field.enum";

export interface SortState<T extends string> {
    field: T;
    direction: SortDirection;
}