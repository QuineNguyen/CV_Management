package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.InAppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    Page<InAppNotification> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<InAppNotification> findByRecipientIdAndReadFalse(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    boolean existsByIdAndRecipientId(UUID id, UUID recipientId);

    // Scoped by recipient in the WHERE clause, so a foreign id matches nothing.
    @Modifying
    @Query("UPDATE InAppNotification n SET n.read = true "
            + "WHERE n.id = :id AND n.recipientId = :recipientId AND n.read = false")
    int markRead(@Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.read = true "
            + "WHERE n.recipientId = :recipientId AND n.read = false")
    int markAllRead(@Param("recipientId") UUID recipientId);
}
