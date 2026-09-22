export enum InlineCommentStatus {
    Open = "OPEN",
    Resolved = "RESOLVED",
}

export const INLINE_COMMENT_STATUS_LABELS: Record<InlineCommentStatus, string> = {
    [InlineCommentStatus.Open]: "Open",
    [InlineCommentStatus.Resolved]: "Resolved",
}