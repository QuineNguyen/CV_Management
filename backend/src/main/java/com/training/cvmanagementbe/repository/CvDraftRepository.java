package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.CvDraft;
import com.training.cvmanagementbe.enums.DraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvDraftRepository extends JpaRepository<CvDraft, UUID> {

    // "At most one open draft" — PUBLISHED and CANCELLED do not count.
    Optional<CvDraft> findByCvIdAndStatusIn(UUID cvId, Collection<DraftStatus> openStatuses);

    boolean existsByCvIdAndStatusIn(UUID cvId, Collection<DraftStatus> statuses);

    List<CvDraft> findByCvIdInAndStatusIn(Collection<UUID> cvIds, Collection<DraftStatus> statuses);
}
