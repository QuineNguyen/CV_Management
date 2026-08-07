package com.training.cvmanagementbe.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Platform status.
 *
 * <p>Reports the applied schema version alongside the time zone and character set actually in
 * effect, rather than a bare "UP". Those three are what silently corrupt data when the application
 * and the database disagree, and reading them from outside the containers is far easier than
 * discovering the mismatch from the data afterwards.
 */
@RestController
@RequestMapping("/health")
@Tag(name = "System")
@SecurityRequirements // public: used by health checks and by the browser before sign-in
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Operation(summary = "Report schema version, time zone and character set")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "serverTime", OffsetDateTime.now(),
                "jvmTimeZone", ZoneId.systemDefault().getId(),
                "dbTimeZone", scalar("SELECT @@session.time_zone"),
                "dbCharset", scalar("SELECT @@character_set_database"),
                "dbCollation", scalar("SELECT @@collation_database"),
                "schemaVersion", scalar(
                        "SELECT version FROM flyway_schema_history WHERE success = 1 "
                                + "ORDER BY installed_rank DESC LIMIT 1")
        );
    }

    /**
     * Returns a placeholder instead of propagating a failure: this endpoint is most useful exactly
     * when something is wrong, so it must still answer when one of these queries cannot run.
     */
    private String scalar(String sql) {
        try {
            return jdbc.queryForObject(sql, String.class);
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
