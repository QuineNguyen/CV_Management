package com.training.cvmanagementbe.smoke;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signed storage URLs must be signed against the address the browser uses.
 *
 * <p>Two halves are checked here, and the second is the one that explains the design:
 *
 * <ol>
 *   <li>a URL signed with the public client contains no internal hostname;</li>
 *   <li>the signature depends on the host, so the URL cannot be corrected on the client by
 *       substituting one. That is why there are two clients in the configuration rather than one
 *       whose output gets rewritten later.</li>
 * </ol>
 *
 * <p>Whether an image actually renders is confirmed by loading one in a browser; this test exists
 * to keep the logic from breaking quietly afterwards.
 */
@Testcontainers
@DisplayName("Storage: signed URLs use the public address")
class PresignedUrlTest {

    private static final MinIOContainer MINIO =
            new MinIOContainer("minio/minio:RELEASE.2024-09-13T20-26-02Z");
    private static final String BUCKET = "cvms";
    private static final String KEY = "smoke/sample.png";

    /** Stands in for the address a user types; deployed, this is the site host plus a path. */
    private static final String PUBLIC_ENDPOINT = "http://cvms.example.com/storage";

    private static MinioClient internal;
    private static MinioClient publicSigner;

    @BeforeAll
    static void prepare() throws Exception {
        MINIO.start();

        internal = MinioClient.builder()
                .endpoint(MINIO.getS3URL())
                .credentials(MINIO.getUserName(), MINIO.getPassword())
                .build();

        publicSigner = MinioClient.builder()
                .endpoint(PUBLIC_ENDPOINT)
                .credentials(MINIO.getUserName(), MINIO.getPassword())
                .build();

        internal.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());

        byte[] minimalPng = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        internal.putObject(PutObjectArgs.builder()
                .bucket(BUCKET).object(KEY)
                .stream(new ByteArrayInputStream(minimalPng), minimalPng.length, -1)
                .contentType("image/png")
                .build());
    }

    @Test
    @DisplayName("A signed URL points at the public host, not the internal one")
    void urlUsesPublicHost() throws Exception {
        String url = publicSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(BUCKET).object(KEY)
                .expiry(15, TimeUnit.MINUTES).build());

        assertThat(URI.create(url).getHost())
                .as("a browser cannot resolve a service name from the private network")
                .isEqualTo("cvms.example.com");
        assertThat(url).doesNotContain("minio:9000");
        assertThat(url).contains("/storage/" + BUCKET + "/" + KEY);
        assertThat(url).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("The signature depends on the host, so rewriting the URL cannot work")
    void signatureCoversHost() throws Exception {
        String internalUrl = internal.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(BUCKET).object(KEY)
                .expiry(15, TimeUnit.MINUTES).build());
        String publicUrl = publicSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(BUCKET).object(KEY)
                .expiry(15, TimeUnit.MINUTES).build());

        assertThat(signatureOf(internalUrl))
                .as("same bucket, same object, different signature - because the host is part of "
                        + "what was signed. Patching the host on the client would produce a URL "
                        + "the object store rejects.")
                .isNotEqualTo(signatureOf(publicUrl));
    }

    private String signatureOf(String url) {
        for (String pair : URI.create(url).getQuery().split("&")) {
            if (pair.startsWith("X-Amz-Signature=")) {
                return pair.substring("X-Amz-Signature=".length());
            }
        }
        throw new IllegalStateException("No signature in URL: " + url);
    }
}
