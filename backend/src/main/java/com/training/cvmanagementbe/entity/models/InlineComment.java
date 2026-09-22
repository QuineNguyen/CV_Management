package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.entity.converter.CvSectionKeyConverter;
import com.training.cvmanagementbe.enums.CvSectionKey;
import com.training.cvmanagementbe.enums.InlineCommentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One comment anchored at (section, item, field) of a draft, in one review round.
 * - Root comments are written by the reviewer when rejecting; replies point at their root
 * through parentCommentId and copy its anchor and round.
 * - Insert-only except for status: a resubmit flips OPEN rows of earlier rounds to RESOLVED.
 */
@Entity
@Table(name = "inline_comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InlineComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    // SMALLINT in the schema; declared so schema validation does not expect INTEGER.
    @Column(name = "review_round", nullable = false, columnDefinition = "SMALLINT")
    private int reviewRound;

    // Same converter as ChangeLogEntry: the CHECK constraint expects snake_key keys.
    @Convert(converter = CvSectionKeyConverter.class)
    @Column(name = "section_key", nullable = false, length = 40)
    private CvSectionKey sectionKey;

    // Required for REPEATED sections, always null for SINGLE ones.
    @Column(name = "item_id", length = 64)
    private String itemId;

    // Null = the comment covers the whole item (or the whole section).
    @Column(name = "field_key", length = 64)
    private String fieldKey;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InlineCommentStatus status;

    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isRoot() {
        return parentCommentId == null;
    }
}
