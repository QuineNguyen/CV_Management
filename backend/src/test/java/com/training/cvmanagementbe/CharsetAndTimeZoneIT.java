package com.training.cvmanagementbe;

import com.training.cvmanagementbe.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Complete" criteria (2) and (4) of Phase 0 (04):
 * insert and read back Japanese string, and deadline 23:59:59 does not shift.
 *
 * <p>Run when Docker is available. Bay #3 if detected and need to
 * <b>stop database volume</b> - so this test must pass BEFORE writing V1.
 */
class CharsetAndTimeZoneIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Database uses utf8mb4 and keeps Vietnamese + Japanese")
    void utf8mb4RoundTrip() {
        assertThat(jdbc.queryForObject("SELECT @@character_set_database", String.class))
                .isEqualTo("utf8mb4");

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS charset_probe (
                  id INT PRIMARY KEY,
                  txt TEXT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        String sample = "Nguyễn Văn Ánh — 日本語のテスト — Kỹ sư cầu nối";
        jdbc.update("REPLACE INTO charset_probe (id, txt) VALUES (1, ?)", sample);

        String readBack = jdbc.queryForObject("SELECT txt FROM charset_probe WHERE id = 1", String.class);
        assertThat(readBack).isEqualTo(sample);
    }

    @Test
    @DisplayName("JVM and database are both +07:00, deadline 23:59:59 does not shift")
    void timeZoneIsHoChiMinh() {
        assertThat(ZoneId.systemDefault().getId()).isEqualTo("Asia/Ho_Chi_Minh");

        String sessionTz = jdbc.queryForObject("SELECT @@session.time_zone", String.class);
        assertThat(sessionTz).isIn("+07:00", "Asia/Ho_Chi_Minh");

        LocalDateTime deadline = LocalDate.of(2026, 3, 31).atTime(23, 59, 59);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS deadline_probe (
                  id INT PRIMARY KEY,
                  deadline DATETIME NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbc.update("REPLACE INTO deadline_probe (id, deadline) VALUES (1, ?)", deadline);

        LocalDateTime readBack = jdbc.queryForObject(
                "SELECT deadline FROM deadline_probe WHERE id = 1", LocalDateTime.class);
        assertThat(readBack).isEqualTo(deadline);
    }
}
