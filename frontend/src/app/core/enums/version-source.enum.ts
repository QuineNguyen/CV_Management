export enum VersionSource {
    Approval = 'APPROVAL',
    DirectEdit = 'DIRECT_EDIT',
    Rollback = 'ROLLBACK',
}

export const VERSION_SOURCE_LABELS: Record<VersionSource, string> = {
    [VersionSource.Approval]: 'Approved',
    [VersionSource.DirectEdit]: 'Published directly by owner',
    [VersionSource.Rollback]: 'Restored from an earlier version',
};