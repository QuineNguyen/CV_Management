package com.training.cvmanagementbe.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Character set and time zone, checked at the database level.
 *
 * <p>The equivalent check across the whole request path is an endpoint that can be exercised from
 * a browser; this is the version that runs in continuous integration, where nobody is available to
 * click anything.
 *
 * <p>Worth having early because the character-set failure is not repairable in place: once
 * migrations have run against real data with the wrong server setting, fixing it means rebuilding
 * the database volume.
 */
@DisplayName("Environment: character set and time zone")
class CharsetAndTimezoneTest extends MariaDBTestBase {

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM update_requests");
        }
    }

    @Test
    @DisplayName("Server and every table use a four-byte character set")
    void fourByteCharacterSet() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT @@character_set_database, @@collation_database")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("utf8mb4");
                assertThat(rs.getString(2)).isEqualTo("utf8mb4_unicode_ci");
            }
            // Checked per table as well: one table created with a different collation fails on its
            // own, and that only surfaces once someone enters non-Latin text into that table.
            try (ResultSet rs = st.executeQuery("""
                    SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                      AND TABLE_COLLATION NOT LIKE 'utf8mb4%'
                    """)) {
                while (rs.next()) {
                    assertThat(rs.getString("TABLE_NAME"))
                            .as("table %s uses collation %s", rs.getString(1), rs.getString(2))
                            .isNull();
                }
            }
        }
    }

    @Test
    @DisplayName("Japanese, Vietnamese and emoji survive a round trip intact")
    void nonLatinTextSurvives() throws Exception {
        // Three scripts, three different failure modes:
        //  - kanji and kana: ordinary Japanese CV content;
        //  - Vietnamese diacritics: corrupted silently under several near-miss encodings;
        //  - emoji: four bytes, which is what separates a full four-byte character set from the
        //    older three-byte one that shares its name.
        String[] samples = {
                "日本語のプロフィール — 株式会社の経験",
                "Kỹ sư phần mềm — Phòng Kỹ thuật Công nghệ",
                "Completed ✅ 🎌"
        };

        try (Connection c = connect()) {
            for (String sample : samples) {
                long id;
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO update_requests (employee_id, profile_id, language, reason,
                            deadline, status, notification_failed,
                            created_by, created_at, updated_by, updated_at)
                        VALUES (1, NULL, 'JA', ?, '2026-12-31 23:59:59', 'COMPLETED', FALSE,
                                1, NOW(), 1, NOW())
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, sample);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT reason, CHAR_LENGTH(reason) FROM update_requests WHERE id = ?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getString(1))
                                .as("text must come back byte for byte, with no substitutions")
                                .isEqualTo(sample);
                        assertThat(rs.getInt(2))
                                .as("character count must match; a mismatch means it was truncated "
                                        + "by byte length rather than character length")
                                .isEqualTo(sample.codePointCount(0, sample.length()));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("An end-of-day deadline reads back with the same date and time")
    void endOfDayDeadlineIsStable() throws Exception {
        LocalDate day = LocalDate.of(2026, 8, 31);
        LocalDateTime deadline = LocalDateTime.of(day, LocalTime.of(23, 59, 59));

        try (Connection c = connect()) {
            // Check the session zone first. Under UTC the assertions below would still pass while
            // the scheduled job fired seven hours off in the deployed system.
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT @@session.time_zone")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("+07:00");
            }

            long id;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO update_requests (employee_id, profile_id, language, reason,
                        deadline, status, notification_failed,
                        created_by, created_at, updated_by, updated_at)
                    VALUES (1, NULL, 'VI', 'Deadline check', ?, 'COMPLETED', FALSE,
                            1, NOW(), 1, NOW())
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setObject(1, deadline);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT deadline, HOUR(deadline), DATE(deadline) FROM update_requests WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getObject(1, LocalDateTime.class))
                            .as("a seven-hour shift means the container or the JDBC session zone "
                                    + "was never set")
                            .isEqualTo(deadline);
                    assertThat(rs.getInt(2)).isEqualTo(23);
                    assertThat(rs.getObject(3, LocalDate.class))
                            .as("the wrong date is the worse outcome: the daily duplicate guard "
                                    + "and the escalation level are both keyed on it")
                            .isEqualTo(day);
                }
            }
        }
    }

    @Test
    @DisplayName("The test JVM itself runs in the expected zone")
    void jvmZoneIsPinned() {
        assertThat(java.time.ZoneId.systemDefault().getId())
                .as("set through the surefire configuration")
                .isEqualTo("Asia/Ho_Chi_Minh");
    }
}
