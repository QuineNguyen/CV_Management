package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByOrderByDisplayOrderAscNameAsc();

    List<Department> findByParentDepartmentIdOrderByDisplayOrderAscNameAsc(UUID parentId);

    List<Department> findByParentDepartmentIdIsNullOrderByDisplayOrderAscNameAsc();

    boolean existsByParentDepartmentId(UUID parentId);

    Optional<Department> findTopByParentDepartmentIdOrderByDisplayOrderDesc(UUID parentId);

    Optional<Department> findTopByParentDepartmentIdIsNullOrderByDisplayOrderDesc();
}
