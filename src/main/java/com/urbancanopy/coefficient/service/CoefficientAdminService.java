package com.urbancanopy.coefficient.service;

import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.coefficient.dto.CoefficientResponse;
import com.urbancanopy.coefficient.entity.EnvironmentalCoefficient;
import com.urbancanopy.coefficient.mapper.CoefficientMapper;
import com.urbancanopy.common.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The administration use cases over the coefficient registry (FR-ADM-04 … FR-ADM-06).
 *
 * <p>Distinct from {@link CoefficientService}, and the split is deliberate. That class is a
 * <em>domain</em> service: it owns the registry, caches it, validates a value against its
 * range, and is called on the hot path by every calculation. This class is a
 * <em>use-case</em> service: it authorises the caller, records what was changed for the
 * audit trail, and translates entities into DTOs for the web boundary.</p>
 *
 * <p>Putting the audit write and the DTO mapping into {@code CoefficientService} would give
 * the engine's coefficient source a dependency on the audit trail and on the shape of an
 * HTTP response — the two things the calculation path has no business knowing about.</p>
 */
@Service
public class CoefficientAdminService {

    private static final String RESOURCE = "EnvironmentalCoefficient";

    private final CoefficientService coefficientService;
    private final CoefficientMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public CoefficientAdminService(CoefficientService coefficientService,
                                   CoefficientMapper mapper,
                                   CurrentUserProvider currentUserProvider,
                                   AuditService auditService) {
        this.coefficientService = coefficientService;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    /** The whole registry, ordered by category then key, as the admin screen renders it. */
    @Transactional(readOnly = true)
    public List<CoefficientResponse> findAll() {
        currentUserProvider.assertAdmin();
        return coefficientService.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoefficientResponse findById(Long id) {
        currentUserProvider.assertAdmin();
        return mapper.toResponse(coefficientService.findById(id));
    }

    /**
     * Revises one coefficient (FR-ADM-05).
     *
     * <p>The previous value is read before the change so the audit entry can record
     * "old → new". A coefficient edit silently rewrites the figures of every plan that has
     * no snapshot, so who changed what, and from what, is the least the trail must hold.</p>
     */
    @Transactional
    public CoefficientResponse updateValue(Long id, BigDecimal newValue) {
        currentUserProvider.assertAdmin();

        BigDecimal previousValue = coefficientService.findById(id).getValue();
        EnvironmentalCoefficient updated = coefficientService.updateValue(id, newValue);

        auditService.recordChange(AuditEventType.COEFFICIENT_CHANGED, RESOURCE, id,
                updated.getCoefficientKey(), previousValue, updated.getValue());

        return mapper.toResponse(updated);
    }

    /** Restores one coefficient to its seeded default (FR-ADM-06). */
    @Transactional
    public CoefficientResponse restoreDefault(Long id) {
        currentUserProvider.assertAdmin();

        BigDecimal previousValue = coefficientService.findById(id).getValue();
        EnvironmentalCoefficient restored = coefficientService.restoreDefault(id);

        auditService.recordChange(AuditEventType.COEFFICIENT_RESTORED, RESOURCE, id,
                restored.getCoefficientKey(), previousValue, restored.getValue());

        return mapper.toResponse(restored);
    }
}
