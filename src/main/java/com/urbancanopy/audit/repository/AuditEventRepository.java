package com.urbancanopy.audit.repository;

import com.urbancanopy.audit.entity.AuditEvent;
import com.urbancanopy.audit.entity.AuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence access for {@link AuditEvent} (FR-ADM-07). */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
            SELECT e FROM AuditEvent e
            LEFT JOIN FETCH e.actor
            WHERE (:eventType IS NULL OR e.eventType = :eventType)
              AND (:actorId   IS NULL OR e.actor.id = :actorId)
            """)
    Page<AuditEvent> search(@Param("eventType") AuditEventType eventType,
                            @Param("actorId") Long actorId,
                            Pageable pageable);
}
