// One piece of an inline text diff inside a field
export enum DiffChunkType {
    Equal = 'EQUAL',
    Insert = 'INSERT',
    Delete = 'DELETE',
}

// Keys the diff reports that no section descriptor declares
export enum CvDiffExtraField {
    AvatarImageId = 'avatar_image_id',
    IsUntranslated = 'is_untranslated',
    DeletedInMaster = 'deleted_in_master',
}

export const CV_DIFF_EXTRA_FIELD_LABELS: Readonly<Record<CvDiffExtraField, string>> = {
    [CvDiffExtraField.AvatarImageId]: 'Photo',
    [CvDiffExtraField.IsUntranslated]: 'Untranslated',
    [CvDiffExtraField.DeletedInMaster]: 'Removed in master',
};

// How the viewer renders a field value
export enum DiffValueKind {
    Text = 'TEXT',
    Image = 'IMAGE',
}

// The two version pickers of the viewer
export enum DiffPickerSide {
    From = 'FROM',
    To = 'TO',
}

// What the viewer lists
export enum DiffViewMode {
    ChangesOnly = 'CHANGES_ONLY',
    FullCv = 'FULL_CV',
}

export const DIFF_VIEW_MODE_LABELS: Readonly<Record<DiffViewMode, string>> = {
    [DiffViewMode.ChangesOnly]: 'Changes only',
    [DiffViewMode.FullCv]: 'Full CV',
};

// Kind of change between two versions.
// UNCHANGED only comes from the version diff, never from a stored change log.
export enum ChangeType {
    Added = 'ADDED',
    Modified = 'MODIFIED',
    Removed = 'REMOVED',
    Unchanged = 'UNCHANGED',
}

export const CHANGE_TYPE_LABELS: Readonly<Record<ChangeType, string>> = {
    [ChangeType.Added]: 'Added',
    [ChangeType.Modified]: 'Modified',
    [ChangeType.Removed]: 'Removed',
    [ChangeType.Unchanged]: 'Unchanged',
};

// Sign in the change gutter ("+" added, "-" removed)
export const CHANGE_TYPE_MARKS: Readonly<Record<ChangeType, string>> = {
    [ChangeType.Added]: '+',
    [ChangeType.Modified]: '~',
    [ChangeType.Removed]: '-',
    [ChangeType.Unchanged]: '',
};