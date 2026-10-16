package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.users.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateTimeJdbcType;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Read model of audit_logs. Rows are written only by AuditLogger (plain JDBC, inside the business
 * transaction), so this entity is never persisted or updated.
 * AuditLogger stores Enum.name(), which is what EnumType.STRING reads back.
 */
@Entity
@Table(name = "audit_logs")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    // Role at the moment of the action; users.role may have changed since
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, updatable = false, length = 40)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    private TargetType targetType;

    // Polymorphic reference, no foreign key
    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "old_value", updatable = false, columnDefinition = "LONGTEXT")
    private String oldValue;

    @Column(name = "new_value", updatable = false, columnDefinition = "LONGTEXT")
    private String newValue;

    // AuditLogger writes LocalDateTime.now() through plain JDBC, with no UTC shift. Reading it
    // through hibernate.jdbc.time_zone=UTC would add the zone offset, so bind and read it as-is.
    @JdbcType(LocalDateTimeJdbcType.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
