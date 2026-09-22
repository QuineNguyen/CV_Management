import { CvSectionKey } from "../enums/cv-section-key.enum";
import { DraftStatus } from "../enums/draft-status.enum";
import { InlineCommentStatus } from "../enums/inline-comment-status.enum";

export interface InlineCommentRequest {
    sectionKey: CvSectionKey;
    itemId: string | null;      // required for REPEATED sections, null for SINGLE ones
    fieldKey: string | null;    // null = the whole entry or section
    content: string;
}

export interface RejectDraftRequest {
    reason: string;
    comments: InlineCommentRequest[];
}

export interface DraftRejectResponse {
    newStatus: DraftStatus;
    commentCount: number;
}

export interface ReplyCommentRequest {
    content: string;
}

// Rides on CvDraftResponse.inlineComments; also returned by reply.
export interface InlineCommentResponse {
    id: string;
    reviewRound: number;
    sectionKey: CvSectionKey;
    itemId: string | null;
    fieldKey: string | null;
    authorName: string | null;
    content: string;
    status: InlineCommentStatus;
    parentCommentId: string | null;
    createdAt: string;
    inlineComments: InlineCommentResponse[];
}