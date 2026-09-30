export enum InlineCommentStatus {
    Open = "OPEN",
    Resolved = "RESOLVED",
}

export const INLINE_COMMENT_STATUS_LABELS: Record<InlineCommentStatus, string> = {
    [InlineCommentStatus.Open]: "Open",
    [InlineCommentStatus.Resolved]: "Resolved",
}

// Dropdowns inside the anchor picker; at most one is open at a time
export enum AnchorSelectKey {
    Section = 'section',
    Item = 'item',
    Field = 'field',
}