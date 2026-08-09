package com.urbancanopy.audit.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.time.Instant;

/**
 * One audit entry as the administrator's log screen renders it (FR-ADM-07).
 *
 * <p>{@code actorName} and {@code actorEmail} are copied out rather than nested as a user object,
 * because the actor reference is nullable by design — it is set to null if an account is ever
 * removed, so the history survives its subject. A flattened row shows "(removed account)" where a
 * nested object would show {@code null} and leave the interface to guess.</p>
 *
 * @param detail the human-readable summary of what changed, e.g. "survival rate 0.85 → 0.80".
 *               Never a credential or a token: an audit log that records secrets is a liability
 *               rather than a control (FR-SYS-08)
 */
public record AuditEventResponse(
        Long id,
        EnumOption eventType,
        Long actorId,
        String actorName,
        String actorEmail,
        String entityType,
        Long entityId,
        String detail,
        String ipAddress,
        Instant occurredAt
) {
}
