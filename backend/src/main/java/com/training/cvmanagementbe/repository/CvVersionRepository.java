package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.CvVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CvVersionRepository extends JpaRepository<CvVersion, UUID> {

    // The current version is derived, not stored: highest version_number wins.
    Optional<CvVersion> findTopByCvIdOrderByVersionNumberDesc(UUID cvId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM CvVersion v WHERE v.cvId = :cvId")
    int findMaxVersionNumber(@Param("cvId") UUID cvId);

    List<CvVersion> findByCvIdOrderByVersionNumberDesc(UUID cvId);

    boolean existsByCvId(UUID cvId);

    // One query for a whole page of CVs instead of one per row.
    @Query("""
            SELECT v.cvId AS cvId, MAX(v.versionNumber) AS versionNumber
              FROM CvVersion v
             WHERE v.cvId IN :cvIds
             GROUP BY v.cvId
            """)
    List<Map<String, Object>> findLatestVersionNumbers(@Param("cvIds") List<UUID> cvIds);
}
