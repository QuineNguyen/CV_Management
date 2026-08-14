package com.training.cvmanagementbe.entity.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Link between an internal account and its Google identity.
 * Created on the first Google sign-in; never creates a user.
 */
@Entity
@Table(name = "external_account_links")
@Getter
@Setter
public class ExternalAccountLink{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // One link per user at most.
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    // Google `sub` claim, unique per Google account.
    @Column(name = "provider_user_id", nullable = false, updatable = false)
    private String providerUserId;

    // Must always equal users.email
    @Column(name = "provider_email", nullable = false, updatable = false)
    private String providerEmail;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;
}
