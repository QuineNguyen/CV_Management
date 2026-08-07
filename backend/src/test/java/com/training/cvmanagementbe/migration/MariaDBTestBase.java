package com.training.cvmanagementbe.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;

/**
 * Shared base for tests that exercise database constraints: a real database server, configured
 * identically to the deployed one.
 *
 * <p>The three settings below are not incidental. Each corresponds to an environment assumption
 * the whole system rests on, and a test running under different settings would prove nothing
 * about the database that actually stores the data:
 *
 * <ul>
 *   <li>a four-byte character set on the server</li>
 *   <li>a fixed local time zone rather than UTC</li>
 *   <li>matching encoding and time zone on the JDBC connection</li>
 * </ul>
 */
@Testcontainers
public abstract class MariaDBTestBase {

    /** Pinned to the deployed version: constraint behaviour is version-specific. */
    protected static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>("mariadb:11.4")
                    .withDatabaseName("cvms")
                    .withUsername("cvms")
                    .withPassword("cvms")
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_unicode_ci",
                            "--default-time-zone=+07:00");

    protected static Flyway flyway;

    /**
     * Starts the container and applies every migration once for the whole test run. Subclasses
     * clean up the rows they insert rather than rebuilding the schema, which keeps a full run to a
     * single container start.
     */
    @BeforeAll
    static void startDatabase() {
        if (!MARIADB.isRunning()) {
            MARIADB.start();
        }
        if (flyway == null) {
            flyway = Flyway.configure()
                    .dataSource(jdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword())
                    .locations("classpath:db/migration")
                    .placeholders(Map.of(
                            "seedAdminEmail", "admin@example.com",
                            "seedAdminUsername", "admin",
                            "seedTechLeadEmail", "techlead@example.com",
                            "seedTechLeadUsername", "techlead",
                            // Not a usable credential, and not the one any environment uses.
                            "seedPasswordHash", "$2a$10$placeholder.for.tests.only.not.a.real.hash",
                            "seedDepartmentCode", "ORG",
                            "seedDepartmentName", "Organisation",
                            "seedTeamCode", "TEAM-ROOT",
                            "seedTeamName", "Root team"))
                    .load();
            flyway.migrate();
        }
    }

    protected static String jdbcUrl() {
        return MARIADB.getJdbcUrl() + "?useUnicode=true&characterEncoding=UTF-8"
                + "&sessionVariables=time_zone='%2B07:00'";
    }

    protected static Connection connect() throws Exception {
        Properties p = new Properties();
        p.setProperty("user", MARIADB.getUsername());
        p.setProperty("password", MARIADB.getPassword());
        return DriverManager.getConnection(jdbcUrl(), p);
    }
}
