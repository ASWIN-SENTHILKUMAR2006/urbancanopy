package com.urbancanopy.audit.entity;

import com.urbancanopy.common.domain.BaseEntity;
import com.urbancanopy.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One recorded security- or data-sensitive event (FR-ADM-07).
 *
 * <p>Append-only, like {@link com.urbancanopy.report.entity.ReportLog}. The actor
 * reference is nullable and set to null on account deletion so the history survives.</p>
 *
 * <p>{@link #detail} holds a short human-readable summary — "survival rate 0.85 → 0.80",
 * "role ROLE_USER → ROLE_ADMIN". It must never contain a password, a token, or anything
 * else that would turn the audit log into a liability (FR-SYS-08).</p>
 */
@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_type_date", columnList = "event_type,occurred_at"),
                @Index(name = "idx_audit_actor", columnList = "actor_id")
        })
public class AuditEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id",
            foreignKey = @ForeignKey(name = "fk_audit_actor"))
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    /** Simple name of the affected entity, e.g. {@code TreeSpecies}. */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
        // Required by JPA.
    }

    public AuditEvent(User actor, AuditEventType eventType, String entityType,
                      Long entityId, String detail, String ipAddress) {
        this.actor = actor;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.occurredAt = Instant.now();
    }

    public User getActor() {
        return actor;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
