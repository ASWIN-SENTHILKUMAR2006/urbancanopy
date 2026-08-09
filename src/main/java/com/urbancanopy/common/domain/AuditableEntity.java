package com.urbancanopy.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Audit columns shared by every mutable entity (FR-SYS-05).
 *
 * <p>Populated automatically by Spring Data JPA auditing, driven by the
 * {@code AuditorAware} bean in {@link com.urbancanopy.config.JpaAuditingConfig}. Keeping
 * these four columns in a {@code @MappedSuperclass} means no entity has to remember to
 * set them, and no service has to remember to ask.</p>
 *
 * <p>Extends {@link BaseEntity}, so an auditable entity also inherits its surrogate key
 * and identity semantics. Append-only records that carry their own timestamp — audit
 * events, report logs, result snapshots — extend {@link BaseEntity} directly instead:
 * a row that is never updated has no use for {@code updated_at} or {@code updated_by}.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 160, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 160)
    private String updatedBy;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
