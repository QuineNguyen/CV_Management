package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/*
 * Skill catalogue. There is deliberately no delete path.
 * The usage count lives in CvSearchRepository - it shares the search's latest-version scope.
 */
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByNameIgnoreCase(String name);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    // Contains-match on name or code; a null pattern lists the whole catalogue
    @Query("""
            SELECT s FROM Skill s
            WHERE :pattern IS NULL
               OR s.name LIKE :pattern
               OR s.code LIKE :pattern
            """)
    Page<Skill> search(@Param("pattern") String pattern, Pageable pageable);
}
