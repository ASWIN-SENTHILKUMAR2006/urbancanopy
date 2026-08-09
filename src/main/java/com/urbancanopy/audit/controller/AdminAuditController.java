package com.urbancanopy.audit.controller;

import com.urbancanopy.audit.dto.AuditEventResponse;
import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The audit trail (FR-ADM-07).
 *
 * <p>Read-only, with no write endpoint of any kind. That is the point of an audit log: entries are
 * created as a side effect of the operations they describe, and an API that could add, edit or
 * remove one would make the whole table unusable as evidence. The table is append-only in the
 * schema for the same reason.</p>
 *
 * <p>Newest first by default — a log is read from the end.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Filterable by event type and by actor, paginated. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditEventResponse>>> search(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) Long actorId,
            @PageableDefault(sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
                auditService.searchEvents(eventType, actorId, pageable)));
    }

    /** Event types, for the filter. */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<EnumOption>>> eventTypes() {
        return ResponseEntity.ok(ApiResponse.ok(auditService.eventTypeOptions()));
    }
}
