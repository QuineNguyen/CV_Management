package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.ChangeLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChangeLogEntryRepository extends JpaRepository<ChangeLogEntry, UUID> {

    List<ChangeLogEntry> findByVersionId(UUID versionId);
}
