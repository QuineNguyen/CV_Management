package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.ExternalAccountLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalAccountLinkRepository extends JpaRepository<ExternalAccountLink, UUID> {

    Optional<ExternalAccountLink> findByUserId(UUID userId);

    Optional<ExternalAccountLink> findByProviderUserId(String providerUserId);
}
