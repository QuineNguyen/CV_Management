// Badge colour family of an audit action, derived from its first word
export enum AuditActionTone {
    Create = 'create',
    Change = 'change',
    Remove = 'remove',
    Neutral = 'neutral',
}

// Change kind of one top-level field; values match the shared [data-change] diff styles
export enum AuditFieldChange {
    Added = 'ADDED',
    Modified = 'MODIFIED',
    Removed = 'REMOVED',
}

// Which side of the detail dialog is shown on phones
export enum AuditValueSide {
    Old = 'old',
    New = 'new',
}