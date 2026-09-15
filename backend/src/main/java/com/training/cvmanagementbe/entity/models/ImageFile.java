package com.training.cvmanagementbe.entity.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row per upload. The object key is never reused, so a re-upload never overwrites an image a
 * published CV version still points at.
 *
 * - Does not extend BaseEntity: the table only records who uploaded and when and the row is
 * immutable once written - there is no updated_by/updated_at to keep.
 * - The two columns are filled here rather than by AuditingEntityListener. That listener drives
 * BaseEntity's four columns and wiring a second path for two columns on one immutable table buys
 * an indirection that fails silently when it does not fire - which is exactly what happened.
 */
@Entity
@Table(name = "image_files")
@Getter
@Setter
public class ImageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "object_key", nullable = false, unique = true, updatable = false)
    private String objectKey;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    // Last line of defence: the columns are NOT NULL and a caller that forgets to set them
    // would otherwise fail at flush time with a constraint error instead of here.
    @PrePersist
    void stampUpload() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
