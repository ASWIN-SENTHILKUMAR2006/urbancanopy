package com.urbancanopy.audit.service;

import com.urbancanopy.audit.dto.AuditEventResponse;
import com.urbancanopy.audit.entity.AuditEvent;
import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.mapper.AuditEventMapper;
import com.urbancanopy.audit.repository.AuditEventRepository;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Records security- and reference-data events (FR-ADM-07).
 *
 * <p>Writes run in {@link Propagation#REQUIRES_NEW} so an audit failure can never roll
 * back the business operation it was describing, and a rolled-back business operation
 * still leaves a record that it was attempted. An audit trail tied to the fate of the
 * transaction it observes is not independent enough to be evidence.</p>
 *
 * <p>Exceptions are swallowed and logged rather than propagated, for the same reason: a
 * full audit table must not stop an administrator from disabling a compromised account.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditEventMapper mapper;

    public AuditService(AuditEventRepository auditEventRepository,
                        UserRepository userRepository,
                        CurrentUserProvider currentUserProvider,
                        AuditEventMapper mapper) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.mapper = mapper;
    }

    /**
     * Records an event attributed to the current caller.
     *
     * @param eventType  what happened
     * @param entityType simple name of the affected entity, may be {@code null}
     * @param entityId   its identifier, may be {@code null}
     * @param detail     short human-readable summary — never a credential or token
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType eventType, String entityType, Long entityId, String detail) {
        try {
            AuditEvent event = new AuditEvent(
                    currentUserProvider.currentUser()
                            .map(user -> userRepository.getReferenceById(user.id()))
                            .orElse(null),
                    eventType, entityType, entityId, truncate(detail), clientIpAddress());
            auditEventRepository.save(event);
        } catch (RuntimeException e) {
            // Never let auditing break the operation it was observing.
            log.warn("Failed to record audit event {} for {} {}: {}",
                    eventType, entityType, entityId, e.getMessage());
        }
    }

    /** Convenience for a value change: records "old → new". */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChange(AuditEventType eventType, String entityType, Long entityId,
                             String field, Object previousValue, Object newValue) {
        record(eventType, entityType, entityId,
                "%s: %s → %s".formatted(field, previousValue, newValue));
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditEventType eventType, Long actorId, Pageable pageable) {
        return auditEventRepository.search(eventType, actorId, pageable);
    }

    /**
     * The trail as the administrator's log screen reads it (FR-ADM-07).
     *
     * <p>Administrator-only, and checked here rather than in the controller so the rule holds for
     * any caller. Mapping happens inside this transaction because {@code actor} is a lazy
     * association and {@code open-in-view} is disabled.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> searchEvents(
            AuditEventType eventType, Long actorId, Pageable pageable) {

        currentUserProvider.assertAdmin();
        return PageResponse.from(
                auditEventRepository.search(eventType, actorId, pageable), mapper::toResponse);
    }

    /** Event-type options for the log screen's filter. */
    public List<EnumOption> eventTypeOptions() {
        currentUserProvider.assertAdmin();
        return EnumOption.from(AuditEventType.class, AuditEventType::getLabel);
    }

    /** Best-effort client address, honouring a reverse proxy's forwarding header. */
    private String clientIpAddress() {
        return currentRequest()
                .map(request -> {
                    String forwarded = request.getHeader("X-Forwarded-For");
                    if (forwarded != null && !forwarded.isBlank()) {
                        return forwarded.split(",")[0].trim();
                    }
                    return request.getRemoteAddr();
                })
                .orElse(null);
    }

    private Optional<HttpServletRequest> currentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 1000 ? detail : detail.substring(0, 997) + "...";
    }
}
