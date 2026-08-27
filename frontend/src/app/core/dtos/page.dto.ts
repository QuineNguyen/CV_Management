// Mirrors PagedResponse on the backend. Reused by every paginated endpoint.
export interface PagedResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

// Query parameters shared by paginated requests
export interface PageQuery {
    page: number;
    size: number;
    sortBy?: string;
    direction?: string;
}