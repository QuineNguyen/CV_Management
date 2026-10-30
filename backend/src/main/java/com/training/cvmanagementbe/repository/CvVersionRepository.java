package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.CvVersion;
import com.training.cvmanagementbe.repository.projection.CvVersionRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface CvVersionRepository extends JpaRepository<CvVersion, UUID> {

    // The current version is derived, not stored: highest version_number wins.
    Optional<CvVersion> findTopByCvIdOrderByVersionNumberDesc(UUID cvId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM CvVersion v WHERE v.cvId = :cvId")
    int findMaxVersionNumber(@Param("cvId") UUID cvId);

    // ---------- Version history & diff ----------

    // Timeline page without the LONGTEXT snapshot columns.
    Page<CvVersionRef> findByCvId(UUID cvId, Pageable pageable);

    // Predecessors (v_{n-1}) of one timeline page.
    List<CvVersionRef> findByCvIdAndVersionNumberIn(UUID cvId, Collection<Integer> versionNumbers);

    // Version numbers of rollback sources.
    List<CvVersionRef> findByIdIn(Collection<UUID> ids);

    // A version resolves only through its own CV, so an id of another CV reads as not found.
    Optional<CvVersion> findByIdAndCvId(UUID id, UUID cvId);
}
