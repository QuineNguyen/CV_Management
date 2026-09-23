package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.InlineComment;
import com.training.cvmanagementbe.enums.approvals.InlineCommentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InlineCommentRepository extends JpaRepository<InlineComment, UUID> {

    // Every round of one draft, oldest first, so threads read top to bottom.
    List<InlineComment> findByDraftIdOrderByCreatedAtAsc(UUID draftId);

    /*
     * Closes the conversation of every earlier round in one statement.
     * Rows are kept, only their status moves.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE InlineComment c SET c.status = :next
            WHERE c.draftId = :draftId AND c.reviewRound < :round AND c.status = :current
            """)
    int resolveEarlierRounds(@Param("draftId") UUID draftId,
                             @Param("round") int currentRound,
                             @Param("current") InlineCommentStatus current,
                             @Param("next") InlineCommentStatus next);

    /*
     * Resolves every open comment of a draft regardless of round. Cancelling ends the whole
     * conversation, so the per-round variant used on resubmit is too narrow here.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE InlineComment c SET c.status = :next
             WHERE c.draftId = :draftId AND c.status = :current
            """)
    int resolveAll(@Param("draftId") UUID draftId,
                   @Param("current") InlineCommentStatus current,
                   @Param("next") InlineCommentStatus next);
}
