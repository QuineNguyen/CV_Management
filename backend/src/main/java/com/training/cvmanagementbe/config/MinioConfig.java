package com.training.cvmanagementbe.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures dual MinIO clients for internal operations and public URL signing.
 *
 * <ul>
 *   <li><b>minioInternal:</b> Handles internal I/O operations (uploads, reads, deletions) within the private network.</li>
 *   <li><b>minioPublic:</b> Used exclusively for generating presigned URLs accessible by external browsers.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(MinioConfig.MinioProperties.class)
public class MinioConfig {
    @ConfigurationProperties(prefix = "app.minio")
    public record MinioProperties(
            /** Service address inside the private network. */
            String internalEndpoint,
            /** Address the browser can reach, including the storage path prefix. */
            String publicEndpoint,
            String publicPathPrefix,
            String accessKey,
            String secretKey,
            String bucket,
            /** Lifetime of a signed URL, in minutes. */
            int presignedMinutes
    ) {
        public MinioProperties {
            if (presignedMinutes <= 0) presignedMinutes = 15;
        }
    }

    @Bean
    MinioClient minioInternal(MinioProperties p) {
        return MinioClient.builder()
                .endpoint(p.internalEndpoint())
                .credentials(p.accessKey(), p.secretKey())
                .build();
    }

    /**
     * Used for signing only. Routing internal traffic through this client would send it out to the
     * reverse proxy and back for no reason.
     */
    @Bean
    MinioClient minioPublic(MinioProperties p) {
        return MinioClient.builder()
                .endpoint(p.publicEndpoint())
                .credentials(p.accessKey(), p.secretKey())
                .build();
    }
}
