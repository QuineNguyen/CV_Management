package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper over the object store that keeps the two endpoints separate:
 * <b>bytes move through the internal endpoint, URLs are signed against the public one</b>.
 */
@Service
public class ImageStorageService {
    private final MinioClient internal;
    private final MinioClient publicSigner;
    private final MinioConfig.MinioProperties props;

    public ImageStorageService(MinioClient minioInternal, MinioClient minioPublic,
                               MinioConfig.MinioProperties props) {
        this.internal = minioInternal;
        this.publicSigner = minioPublic;
        this.props = props;
    }

    /** Uploads through the internal endpoint. Returns the object key. */
    public String upload(MultipartFile file, String objectKey) throws Exception {
        try (InputStream in = file.getInputStream()) {
            internal.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    // The object store keeps the content type in the object's own metadata and
                    // returns it with the signed URL, so the database does not keep a second copy
                    // that could disagree with it.
                    .contentType(file.getContentType() == null
                            ? "application/octet-stream" : file.getContentType())
                    .build());
        }
        return objectKey;
    }

    /**
     * Signs a time-limited URL against the <b>public</b> endpoint.
     *
     * <p>The resulting URL points at the address the browser can actually resolve, and the reverse
     * proxy forwards it to the object store without altering the host header. Both halves are
     * required: the signature includes the host, so if either side substitutes a different one the
     * object store rejects the request.
     */
    public String presignedUrl(String objectKey) throws Exception {
        return publicSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(props.bucket())
                .object(objectKey)
                .expiry(props.presignedMinutes(), TimeUnit.MINUTES)
                .build());
    }

    /** Reads raw bytes through the internal endpoint; used when embedding images in exports. */
    public byte[] readBytes(String objectKey) throws Exception {
        try (InputStream in = internal.getObject(GetObjectArgs.builder()
                .bucket(props.bucket()).object(objectKey).build())) {
            return in.readAllBytes();
        }
    }

    /**
     * Removes an object.
     *
     * <p>Order is not negotiable: delete the database row first and call this only after that
     * commit succeeds. The database is what knows whether a published version still references the
     * image; deleting the object first would leave a permanent record pointing at nothing.
     */
    public void delete(String objectKey) throws Exception {
        internal.removeObject(RemoveObjectArgs.builder()
                .bucket(props.bucket()).object(objectKey).build());
    }

    public String bucket() {
        return props.bucket();
    }

    /**
     * Inserts the nginx path prefix that the MinIO SDK cannot carry in its endpoint.
     * Raw path and query are used so the SigV4 signature is never re-encoded.
     */
    private String withPublicPrefix(String signedUrl) {
        String prefix = props.publicPathPrefix();
        if (prefix == null || prefix.isBlank()) {
            return signedUrl;
        }
        URI uri = URI.create(signedUrl);
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return uri.getScheme() + "://" + uri.getAuthority() + prefix + uri.getRawPath() + query;
    }
}
