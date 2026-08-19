package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    boolean existsByDepartmentId(UUID departmentId);
}
