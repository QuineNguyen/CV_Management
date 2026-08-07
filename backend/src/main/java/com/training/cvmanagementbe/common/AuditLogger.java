package com.training.cvmanagementbe.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.enums.Action;
import com.training.cvmanagementbe.enums.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Component responsible for recording audit trail entries for system actions.
 *
 * <p>Enforces three key audit properties:
 * <ol>
 *   <li><b>Real Actor Attribution:</b> Resolves the actor via {@link CurrentActor} without system fallbacks,
 *       ensuring every action is tied to an accountable user.</li>
 *   <li><b>Role Snapshotting:</b> Captures the actor's role at the exact moment of execution, preserving historical
 *       accuracy even if the user's role changes later.</li>
 *   <li><b>Transactional Integrity:</b> Joins the active business transaction via {@link Propagation#MANDATORY}.
 *       If the transaction rolls back, the audit log entry is also rolled back.</li>
 * </ol>
 *
 * <p>Audit logging applies to administrative actions, direct edits, rollbacks, approval reassignments,
 * draft cancellations, and account status changes.
 */
@Component
public class AuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogger.class);

    private static final String SQL = """
            INSERT INTO audit_logs
              (actor_id, actor_role, action, target_type, target_id, old_value, new_value, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditLogger(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Action action, TargetType targetType, UUID targetId,
                       Object oldValue, Object newValue) {

        CurrentActor.Actor actor = CurrentActor.get().orElseThrow(() -> new IllegalStateException(
                "Cannot write an audit entry without an actor. Background work must use "
                        + "CurrentActor.runAs with the person who triggered it. action=" + action));

        jdbc.update(SQL,
                actor.userId(),
                actor.role().name(),
                action.name(),
                targetType.name(),
                targetId,
                serialise(oldValue),
                serialise(newValue),
                LocalDateTime.now());

        logger.debug("audit: {} {} #{} by user#{} ({})",
                action, targetType, targetId, actor.userId(), actor.role());
    }

    private String serialise(Object value) {
        if (value == null) return null;
        if (value instanceof CharSequence cs) return cs.toString();
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // A serialisation problem must not roll back the business change it describes.
            // Degrading to toString keeps the entry, which is the part that matters.
            logger.warn("Could not serialise an audit value ({}); falling back to toString",
                    e.getMessage());
            return String.valueOf(value);
        }
    }
}
