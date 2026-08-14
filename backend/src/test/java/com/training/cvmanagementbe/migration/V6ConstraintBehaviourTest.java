package com.training.cvmanagementbe.migration;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An index existing does not prove it blocks the right things. These tests cover the three
 * branches that are easiest to get wrong, and where a mistake goes straight into the data:
 *
 * <ol>
 *   <li>genuine duplicates are rejected;</li>
 *   <li>soft-deleted rows release their slot - blocking here instead would make the restore and
 *       re-create flows impossible to reach;</li>
 *   <li>a key column holding NULL still blocks duplicates.</li>
 * </ol>
 */
@DisplayName("Schema: constraint behaviour")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class V6ConstraintBehaviourTest extends MariaDBTestBase {
    private static final String ADMIN = "018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02";   // seeded root administrator
    private static final String TEAM = "018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c04";

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : new String[]{"update_requests", "cvs", "cv_profiles"}) {
                st.execute("DELETE FROM " + table);
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    @Order(1)
    @DisplayName("Two active profiles cannot share a name for the same employee")
    void duplicateProfileNameRejected() throws Exception {
        try (Connection c = connect()) {
            addProfile(c, "Software Developer", false, "ACTIVE");
            assertThatThrownBy(() -> addProfile(c, "Software Developer", false, "ACTIVE"))
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class)
                    .hasMessageContaining("uk_cv_profiles_active_name");
        }
    }

    @Test
    @Order(2)
    @DisplayName("A deleted profile does not hold on to its name")
    void deletedProfileReleasesName() throws Exception {
        try (Connection c = connect()) {
            addProfile(c, "AI Engineer", false, "DELETED");
            // Must succeed. If it did not, the flow where a restored profile is renamed because
            // its old name was reused could never occur.
            assertThatNoException().isThrownBy(() -> addProfile(c, "AI Engineer", false, "ACTIVE"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("One primary profile per employee, and zero is valid")
    void singlePrimaryProfile() throws Exception {
        try (Connection c = connect()) {
            // Having no profile at all is legitimate, so there is nothing to assert for that case
            // beyond the absence of a constraint firing.
            addProfile(c, "Profile A", true, "ACTIVE");
            assertThatThrownBy(() -> addProfile(c, "Profile B", true, "ACTIVE"))
                    .hasMessageContaining("uk_cv_profiles_active_primary");
            assertThatNoException().isThrownBy(() -> addProfile(c, "Profile C", true, "DELETED"));
        }
    }

    @Test
    @Order(4)
    @DisplayName("One active CV per language, and one master per profile")
    void cvUniquenessRules() throws Exception {
        try (Connection c = connect()) {
            String profile = addProfile(c, "CV profile", true, "ACTIVE");

            String master = addCv(c, profile, "VI", null, "ACTIVE");
            assertThatThrownBy(() -> addCv(c, profile, "VI", master, "ACTIVE"))
                    .hasMessageContaining("uk_cvs_active_profile_lang");

            // A second CV must point at the master; leaving the parent empty makes it a second one.
            assertThatNoException().isThrownBy(() -> addCv(c, profile, "EN", master, "ACTIVE"));
            assertThatThrownBy(() -> addCv(c, profile, "JA", null, "ACTIVE"))
                    .hasMessageContaining("uk_cvs_active_master");

            // Deleting the Vietnamese CV frees that language slot again.
            try (Statement st = c.createStatement()) {
                st.executeUpdate("UPDATE cvs SET lifecycle_status = 'DELETED' WHERE id = '" + master + "'");
            }
            assertThatNoException().isThrownBy(() -> addCv(c, profile, "VI", null, "ACTIVE"));
        }
    }

    @Test
    @Order(5)
    @DisplayName("Pending requests with no target profile are still deduplicated")
    void nullTargetStillDeduplicated() throws Exception {
        try (Connection c = connect()) {
            addRequest(c, null, "VI", "PENDING");

            // The trap: without collapsing NULL to a sentinel in the generated column, this second
            // insert would succeed, because NULLs do not collide in a unique index.
            assertThatThrownBy(() -> addRequest(c, null, "VI", "PENDING"))
                    .hasMessageContaining("uk_update_requests_pending");

            // A different language is a different key.
            assertThatNoException().isThrownBy(() -> addRequest(c, null, "EN", "PENDING"));
            // A closed request no longer occupies the slot.
            assertThatNoException().isThrownBy(() -> addRequest(c, null, "VI", "COMPLETED"));
        }
    }

    @Test
    @Order(6)
    @DisplayName("A CV from another profile cannot be linked to a request")
    void compositeKeyBlocksMismatch() throws Exception {
        try (Connection c = connect()) {
            String profileA = addProfile(c, "Profile A", true, "ACTIVE");
            String profileB = addProfile(c, "Profile B", false, "ACTIVE");
            String cvOfB = addCv(c, profileB, "VI", null, "ACTIVE");

            String request = addRequest(c, profileA, "VI", "PENDING");
            try (Statement st = c.createStatement()) {
                assertThatThrownBy(() -> st.executeUpdate(
                        "UPDATE update_requests SET cv_id = '" + cvOfB + "' WHERE id = '" + request + "'"))
                        .hasMessageContaining("fk_ur_cv_profile");
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private String addProfile(Connection c, String name, boolean primary, String status)
            throws Exception {
        String id = UUID.randomUUID().toString();
        insert(c, """
                INSERT INTO cv_profiles (id, employee_id, name, is_primary, linked_team_id,
                    lifecycle_status, created_by, created_at, updated_by, updated_at)
                VALUES ('%s', '%s', '%s', %b, '%s', '%s', '%s', NOW(), '%s', NOW())
                """.formatted(id, ADMIN, name, primary, TEAM, status, ADMIN, ADMIN));
        return id;
    }

    private String addCv(Connection c, String profile, String language, String master, String status)
            throws Exception {
        String id = UUID.randomUUID().toString();
        insert(c, """
                INSERT INTO cvs (id, profile_id, language, master_cv_id, lifecycle_status,
                    created_by, created_at, updated_by, updated_at)
                VALUES ('%s', '%s', '%s', %s, '%s', '%s', NOW(), '%s', NOW())
                """.formatted(id, profile, language, master == null ? "NULL" : "'" + master + "'",
                status, ADMIN, ADMIN));
        return id;
    }

    private String addRequest(Connection c, String profile, String language, String status)
            throws Exception {
        String id = UUID.randomUUID().toString();
        insert(c, """
                INSERT INTO update_requests (id, employee_id, profile_id, language, reason,
                    deadline, status, notification_failed, created_by, created_at, updated_by, updated_at)
                VALUES ('%s', '%s', %s, '%s', 'Test reason', '2026-12-31 23:59:59', '%s', FALSE,
                        '%s', NOW(), '%s', NOW())
                """.formatted(id, ADMIN, profile == null ? "NULL" : "'" + profile + "'", language,
                status, ADMIN, ADMIN));
        return id;
    }

    private void insert(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
