package com.training.emailservice.repository;

import com.training.emailservice.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {
}
