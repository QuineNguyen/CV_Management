import { InlineCommentRequest, InlineCommentResponse } from "../dtos/inline-comment.dto";
import { InlineCommentStatus } from "../enums/inline-comment-status.enum";
import { CV_SECTIONS, SectionDescriptor } from "./cv-section-descriptor.model";

export const INLINE_COMMENT_MAX_LENGTH = 2000;

// A root comment with its replies, oldest first.
export interface InlineCommentThread {
    root: InlineCommentResponse;
    replies: InlineCommentResponse[];
}

// A comment staged in the reject form, not yet sent.
export interface PendingInlineComment {
    localId: string;
    request: InlineCommentRequest;
    anchorLabel: string;
}

// Threads of one entry, labelled with that entry as the reader sees it.
export interface EntryThreadGroup {
    label: string;
    threads: InlineCommentThread[];
}

// One entry a reviewer can pin a comment to.
export interface AnchorItemOption {
    id: string;
    label: string;
}

// Groups a flat list into threads; input order (createdAt asc) is kept.
export function buildThreads(comments: readonly InlineCommentResponse[]): InlineCommentThread[]
{
    const threads = new Map<string, InlineCommentThread>();

    for (const comment of comments) {
        if (!comment.parentCommentId) {
            threads.set(comment.id, { root: comment, replies: [] });
        }
    }
    for (const comment of comments) {
        if (comment.parentCommentId) {
            threads.get(comment.parentCommentId)?.replies.push(comment);
        }
    }
    return [...threads.values()];
}

export function isOpenThread(thread: InlineCommentThread): boolean {
    return thread.root.status === InlineCommentStatus.Open;
}

export function sectionOf(sectionKey: string): SectionDescriptor | undefined {
    return CV_SECTIONS.find(section => section.key === sectionKey);
}

export function fieldLabelOf(sectionKey: string, fieldKey: string | null): string | null {
    if (!fieldKey) {
        return null;
    }
    return sectionOf(sectionKey)?.fields.find(field => field.key === fieldKey)?.label ?? fieldKey;
}

// Title of an entry as the editor header shows it or a positional fallback.
export function itemTitleOf(section: SectionDescriptor, item: Record<string, unknown>, index: number): string {
    const raw = section.titleField ? item[section.titleField] : null;
    const title = raw === null || raw === undefined ? "" : String(raw).trim();
    return title.length ? title : `Untitled ${section.itemNoun ?? "entry"} #${index + 1}`;
}

export function anchorLabelOf(sectionKey: string, itemTitle: string | null, fieldKey: string | null): string {
    return [sectionOf(sectionKey)?.label ?? sectionKey, itemTitle, fieldLabelOf(sectionKey, fieldKey)]
        .filter((part): part is string => !!part)
        .join(" › ");
}