package com.training.cvmanagementbe.config;

import com.training.cvmanagementbe.entity.models.CurrentActor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.UUID;

/**
 * Configures JPA Auditing to automatically populate {@code created_by} and {@code updated_by} fields.
 *
 * <p>Uses {@link CurrentActor} to resolve the current user's ID. Returns empty if unauthenticated,
 * ensuring unattributed database writes fail fast.
 */
@Configuration
public class JpaAuditingConfig {
    @Bean
    AuditorAware<UUID> auditorAware() {
        return () -> CurrentActor.get().map(CurrentActor.Actor::userId);
    }
}
