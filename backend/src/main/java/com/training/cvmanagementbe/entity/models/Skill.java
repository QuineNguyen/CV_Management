package com.training.cvmanagementbe.entity.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One entry of the shared skill catalogue.
 * - Never deleted: Published versions point at it by id inside content_json, where no
 * foreign key can block a deletion. An unused skill is pushed down via display_order instead.
 * - No updated_by/update_at columns; every change is in the audit log.
 */
@Entity
@Table(name = "skills")
@Getter
@Setter
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // UPPER_SNAKE_CASE, unique
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    // Unique; case-insensitive under the table collation
    @Column(name = "name", nullable = false)
    private String name;

    // Not unique; every list sorts by (display_order, name)
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_by", updatable = false, nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
