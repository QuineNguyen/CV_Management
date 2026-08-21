package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Page<Department> findByParentDepartmentIdIsNull(Pageable pageable);

    List<Department> findAllByOrderByDisplayOrderAscNameAsc();

    List<Department> findByParentDepartmentIdOrderByDisplayOrderAscNameAsc(UUID parentId);

    List<Department> findByParentDepartmentIdIsNullOrderByDisplayOrderAscNameAsc();

    boolean existsByParentDepartmentId(UUID parentId);

    /*
     * Flat paginated lookup for the parent picker.
     * excludedIds is never empty - the service passes a dummy id when nothing is excluded
     * because JPQL cannot bind an empty IN list.
     */
    @Query("""
            SELECT d FROM Department d
            where d.id NOT IN :excludedIds
            AND (:keyword IS NULL 
                OR LOWER(d.code) LIKE :keyword
                OR LOWER(d.name) LIKE :keyword)
            """)
    Page<Department> search(@Param("keyword") String keyword,
                            @Param("excludedIds") Collection<UUID> excludedIds,
                            Pageable pageable);
}
