export enum CvUpdateStatus {
    Updated = 'UPDATED',
    NotUpdated = 'NOT_UPDATED',
}

export const CV_UPDATE_STATUS_LABELS: Record<CvUpdateStatus, string> = {
    [CvUpdateStatus.Updated]: 'Updated',
    [CvUpdateStatus.NotUpdated]: 'Not updated',
};

// Dropdowns of the CV search filter bar; only one is open at a time
export enum CvSearchDropdown {
    Department = 'department',
    Team = 'team',
    Language = 'language',
    UpdateStatus = 'updateStatus',
}

// Which profiles of each employee a search covers
export enum ProfileScope {
    Primary = 'primary',
    All = 'all',
}

// Child segments under /cvs/:id
export enum CvRouteSegment {
    Edit = 'edit',
    History = 'history',
}