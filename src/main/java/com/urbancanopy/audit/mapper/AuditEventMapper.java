package com.urbancanopy.audit.mapper;

import com.urbancanopy.audit.dto.AuditEventResponse;
import com.urbancanopy.audit.entity.AuditEvent;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * Translates {@link AuditEvent} into its response DTO.
 *
 * <p>Must be called inside the caller's transaction: {@code actor} is a lazy association, and the
 * audit search fetch-joins it precisely so this mapper can read a name without issuing a query per
 * row.</p>
 */
@Component
public class AuditEventMapper {

    public AuditEventResponse toResponse(AuditEvent event) {
        User actor = event.getActor();

        return new AuditEventResponse(
                event.getId(),
                new EnumOption(event.getEventType().name(), event.getEventType().getLabel(), null),
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getFullName(),
                actor == null ? null : actor.getEmail(),
                event.getEntityType(),
                event.getEntityId(),
                event.getDetail(),
                event.getIpAddress(),
                event.getOccurredAt());
    }
}
