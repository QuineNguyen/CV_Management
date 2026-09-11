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
 * immutable once written - there is no updated_by/updated_at to keep. The two columns it does
 * have are still filled by the auditing listener rather than by hand.
 */
@Entity
@Table(name = "image_files")
@EntityListeners(AuditingEntityListener.class)
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
}
