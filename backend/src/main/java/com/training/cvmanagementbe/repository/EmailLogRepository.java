package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.EmailLog;
import com.training.cvmanagementbe.enums.notifications.EmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    // The broker refused the message: the email never left, and the log says why.
    @Modifying
    @Query("UPDATE EmailLog e SET e.status = :status, e.errorMessage = :error WHERE e.id = :id")
    int markUndelivered(@Param("id") UUID id,
                        @Param("status") EmailStatus status,
                        @Param("error") String error);
}
