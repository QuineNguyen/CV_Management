package com.training.cvmanagementbe.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Nen cho moi test tich hop. Rang buoc CSDL phai chay tren MariaDB THAT
 * (04 §1.1) - 8 unique index cua V6 khong the kiem bang H2.
 *
 * <p>Container duoc cau hinh giong het production ve <b>bo ma ky tu</b> va
 * <b>mui gio</b>, neu khong thi test se xanh o day va do o Docker.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
public abstract class AbstractIntegrationTest {

    protected static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>(DockerImageName.parse("mariadb:11.4"))
                    .withDatabaseName("cvms")
                    .withUsername("cvms")
                    .withPassword("cvms")
                    .withEnv("TZ", "Asia/Ho_Chi_Minh")
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_unicode_ci",
                            "--default-time-zone=+07:00")
                    .withReuse(true);

    protected static final MinIOContainer MINIO =
            new MinIOContainer(DockerImageName.parse("minio/minio:latest"))
                    .withUserName("testuser")
                    .withPassword("testpassword")
                    .withReuse(true);

    static {
        MARIADB.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MARIADB.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&connectionTimeZone=Asia/Ho_Chi_Minh&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);

        registry.add("minio.internal-endpoint", MINIO::getS3URL);
        registry.add("minio.public-endpoint", MINIO::getS3URL);
        registry.add("minio.access-key", MINIO::getUserName);
        registry.add("minio.secret-key", MINIO::getPassword);
        registry.add("minio.bucket", () -> "cv-avatars");

        registry.add("app.jwt.secret", () -> "test_secret_toi_thieu_32_byte_cho_thuat_toan_HS256");
    }
}
