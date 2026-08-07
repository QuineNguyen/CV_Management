package com.training.cvmanagementbe.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seed migration runs with foreign key checking disabled, which is the only way to break the
 * cycle between team membership, team leadership, and the mutual reference between departments and
 * users. The cost is that the database cannot report whether the result is consistent. This test
 * is that check.
 */
@DisplayName("Seed: the bootstrap breaks the cycle without leaving inconsistency")
class SeedBootstrapTest extends MariaDBTestBase {

    @Test
    @DisplayName("Every reference written while checking was disabled resolves")
    void referencesResolve() throws Exception {
        assertThat(count("""
                SELECT COUNT(*) FROM users u
                WHERE NOT EXISTS (SELECT 1 FROM departments d WHERE d.id = u.primary_department_id)
                """)).as("dangling department reference").isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM users u
                WHERE NOT EXISTS (SELECT 1 FROM users c WHERE c.id = u.created_by)
                """)).as("the self-referencing first row").isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM departments d
                WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = d.created_by)
                """)).as("dangling creator on the root department").isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM teams t
                WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = t.tech_lead_id)
                """)).as("dangling team lead").isZero();
    }

    @Test
    @DisplayName("The membership and leadership rules hold immediately after seeding")
    void membershipRulesHold() throws Exception {
        assertThat(count("""
                SELECT COUNT(*) FROM users u
                WHERE NOT EXISTS (SELECT 1 FROM team_members m
                                  WHERE m.user_id = u.id AND m.is_primary_team = TRUE)
                """)).as("every account needs exactly one primary team").isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM teams t
                JOIN users u ON u.id = t.tech_lead_id
                WHERE u.role <> 'TECH_LEAD' OR u.status <> 'ACTIVE'
                """)).as("a team's lead must be active and hold the role").isZero();
    }

    @Test
    @DisplayName("No hidden superuser, and both accounts must choose a new password")
    void noPrivilegedBackDoor() throws Exception {
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'"))
                .as("exactly one active administrator to start from").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM users WHERE must_change_password = TRUE"))
                .as("the seeded credential is a bootstrap value, not a password")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("No business data is seeded")
    void noBusinessData() throws Exception {
        assertThat(count("SELECT COUNT(*) FROM cv_profiles")).isZero();
        assertThat(count("SELECT COUNT(*) FROM cvs")).isZero();
        assertThat(count("SELECT COUNT(*) FROM skills"))
                .as("the skill catalogue is built by the people using it, not pre-filled")
                .isZero();
    }

    @Test
    @DisplayName("Default configuration is present")
    void defaultConfiguration() throws Exception {
        assertThat(count("SELECT COUNT(*) FROM system_configs")).isEqualTo(4);
        assertThat(text("SELECT config_value FROM system_configs WHERE config_key='reminder_send_hour'"))
                .isEqualTo("9");
        assertThat(text("SELECT config_value FROM system_configs WHERE config_key='approval_sla_days'"))
                .isEqualTo("3");
    }

    private long count(String sql) throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String text(String sql) throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
