package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.ApprovalDecision;
import com.training.cvmanagementbe.enums.approvals.DecisionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, UUID> {

    // The verdict of one level in one round, if it has been given.
    Optional<ApprovalDecision> findByDraftIdAndLevelAndReviewRound(UUID draftId,
                                                                   int level,
                                                                   int reviewRound);

    /**
     * Every verdict a draft has collected, oldest first. A reviewer opening round 3 needs to read
     * why rounds 1 and 2 were rejected before deciding again.
     */
    List<ApprovalDecision> findByDraftIdOrderByDecidedAtAsc(UUID draftId);

    Optional<ApprovalDecision> findByDraftIdAndLevelAndReviewRoundAndResult(
            UUID draftId, int level, int reviewRound, DecisionResult result);
}
