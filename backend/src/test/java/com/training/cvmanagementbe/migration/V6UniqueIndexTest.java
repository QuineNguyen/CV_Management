package com.training.cvmanagementbe.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that every migration applies and that the eight conditional uniqueness constraints
 * exist.
 *
 * <p>Worth having because of how this fails otherwise: an index dropped during a rebase, or
 * renamed without updating the exception handler, removes a business rule <b>silently</b>. The
 * application keeps working and only the data goes wrong, which is the most expensive way to find
 * out.
 */
@DisplayName("Schema: conditional uniqueness constraints")
class V6UniqueIndexTest extends MariaDBTestBase {

    @Test
    @DisplayName("Every migration applied successfully, in order")
    void allMigrationsApplied() throws Exception {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT version, success FROM flyway_schema_history "
                             + "WHERE type <> 'SCHEMA' ORDER BY installed_rank");
             ResultSet rs = ps.executeQuery()) {

            List<String> applied = new ArrayList<>();
            while (rs.next()) {
                assertThat(rs.getBoolean("success"))
                        .as("migration V%s must succeed", rs.getString("version"))
                        .isTrue();
                applied.add(rs.getString("version"));
            }
            assertThat(applied).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        }
    }

    @ParameterizedTest(name = "[{index}] {1} on {0}")
    @DisplayName("All eight indexes exist and are actually unique")
    @CsvSource({
            "cv_profiles,          uk_cv_profiles_active_primary,    one primary profile per employee",
            "cv_profiles,          uk_cv_profiles_active_name,       profile names unique per employee",
            "cvs,                  uk_cvs_active_profile_lang,       one active CV per language",
            "cvs,                  uk_cvs_active_master,             one master CV per profile",
            "cv_drafts,            uk_cv_drafts_open,                one open draft per CV",
            "approval_assignments, uk_approval_assignments_assigned, one open assignment per draft",
            "update_requests,      uk_update_requests_pending,       one pending request per target",
            "reminder_logs,        uk_reminder_logs_daily,           one reminder per day"
    })
    void indexExistsAndIsUnique(String table, String indexName, String rule) throws Exception {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT NON_UNIQUE
                     FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                     GROUP BY NON_UNIQUE
                     """)) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("%s.%s is missing, so the rule '%s' is no longer enforced",
                                table, indexName, rule)
                        .isTrue();
                assertThat(rs.getInt("NON_UNIQUE"))
                        .as("%s must be unique or it enforces nothing", indexName)
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Seven of the eight sit on stored generated columns")
    void conditionalKeysAreGenerated() throws Exception {
        // The eighth is a plain unique key, because all of its columns are already NOT NULL.
        List<String[]> generatedColumns = List.of(
                new String[]{"cv_profiles",          "uk_active_primary"},
                new String[]{"cv_profiles",          "uk_active_name"},
                new String[]{"cvs",                  "uk_active_profile_lang"},
                new String[]{"cvs",                  "uk_active_master"},
                new String[]{"cv_drafts",            "uk_open_draft"},
                new String[]{"approval_assignments", "uk_assigned_draft"},
                new String[]{"update_requests",      "uk_pending_key"}
        );

        try (Connection c = connect()) {
            for (String[] column : generatedColumns) {
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT EXTRA, GENERATION_EXPRESSION
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                        """)) {
                    ps.setString(1, column[0]);
                    ps.setString(2, column[1]);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as("%s.%s must exist", column[0], column[1]).isTrue();
                        assertThat(rs.getString("EXTRA"))
                                .as("%s.%s must be generated; an ordinary column would have to be "
                                        + "maintained by the application, which will eventually "
                                        + "forget", column[0], column[1])
                                .containsIgnoringCase("STORED");
                        assertThat(rs.getString("GENERATION_EXPRESSION"))
                                .as("%s.%s must have an expression", column[0], column[1])
                                .isNotBlank();
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("The pending-request key collapses NULL to a sentinel")
    void pendingKeyHandlesNull() throws Exception {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT GENERATION_EXPRESSION FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'update_requests' AND COLUMN_NAME = 'uk_pending_key'
                     """);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1).toLowerCase())
                    .as("without COALESCE, a request with no target profile could be created "
                            + "repeatedly and the employee would be emailed about the same thing "
                            + "over and over")
                    .contains("coalesce");
        }
    }

    @Test
    @DisplayName("The composite key on cvs exists for the request foreign key to reference")
    void compositeKeyTargetExists() throws Exception {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT COUNT(*) FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cvs'
                       AND INDEX_NAME = 'uk_cvs_id_profile' AND NON_UNIQUE = 0
                     """);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("without it the composite foreign key cannot be created, and keeping a "
                            + "request's CV and profile consistent falls back to service code")
                    .isEqualTo(2);   // two columns
        }
    }
}
