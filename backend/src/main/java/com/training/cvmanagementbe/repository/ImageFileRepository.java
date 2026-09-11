package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.ImageFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImageFileRepository extends JpaRepository<ImageFile, UUID> {
}
