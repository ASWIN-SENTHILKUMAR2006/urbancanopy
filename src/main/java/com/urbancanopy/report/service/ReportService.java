package com.urbancanopy.report.service;

import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.plan.dto.EstimateResponse;
import com.urbancanopy.plan.dto.PlanComparisonResponse;
import com.urbancanopy.plan.repository.PlantationPlanRepository;
import com.urbancanopy.plan.service.PlanComparisonService;
import com.urbancanopy.plan.service.PlanEstimateService;
import com.urbancanopy.report.dto.ReportLogResponse;
import com.urbancanopy.report.entity.ReportLog;
import com.urbancanopy.report.entity.ReportType;
import com.urbancanopy.report.pdf.ComparisonReportWriter;
import com.urbancanopy.report.pdf.PlanImpactReportWriter;
import com.urbancanopy.report.pdf.ReportFormatter;
import com.urbancanopy.report.repository.ReportLogRepository;
import com.urbancanopy.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates downloadable reports and records that they were generated
 * (FR-RPT-01, FR-RPT-05 … FR-RPT-08).
 *
 * <h2>Authorisation is not re-implemented here</h2>
 * <p>A report is a rendering of a result the caller is already entitled to see, so this service
 * obtains that result through {@link PlanEstimateService} and {@link PlanComparisonService} —
 * the same paths the screens use, with the same ownership checks and the same audit trail for
 * administrator access. Re-deriving the rule here would create a second place for it to be
 * wrong, and the export path is exactly where a leak matters most.</p>
 *
 * <h2>Every generation is recorded</h2>
 * <p>FR-RPT-06: who produced which document, when, and how large it was. The record is written
 * after the bytes exist, so a failed render leaves no entry claiming a document that was never
 * produced.</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final PlanEstimateService estimateService;
    private final PlanComparisonService comparisonService;
    private final ReportLogRepository reportLogRepository;
    private final PlantationPlanRepository planRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ReportFormatter formatter;

    public ReportService(PlanEstimateService estimateService,
                         PlanComparisonService comparisonService,
                         ReportLogRepository reportLogRepository,
                         PlantationPlanRepository planRepository,
                         UserRepository userRepository,
                         CurrentUserProvider currentUserProvider,
                         AuditService auditService,
                         ReportFormatter formatter) {
        this.estimateService = estimateService;
        this.comparisonService = comparisonService;
        this.reportLogRepository = reportLogRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.formatter = formatter;
    }

    // ------------------------------------------------------------------ plan report

    /** The full impact report for one plan (FR-RPT-01, FR-RPT-02). */
    @Transactional
    public GeneratedReport planReport(Long planId) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();

        // Ownership, admin read-through and the ADMIN_VIEWED_PLAN audit entry all happen here.
        EstimateResponse estimate = estimateService.estimate(planId);

        String reportId = newReportId("PLAN");
        String generatedAt = formatter.dateTime(Instant.now());

        byte[] content = new PlanImpactReportWriter(
                estimate, caller.fullName(), reportId, generatedAt, formatter).write();

        String fileName = fileName(estimate.planTitle(), "impact-report");
        record(ReportType.PLAN_IMPACT, planId, null, fileName, content.length, reportId);

        return new GeneratedReport(fileName, content);
    }

    // ------------------------------------------------------------ comparison report

    /** The side-by-side comparison report (FR-RPT-05). */
    @Transactional
    public GeneratedReport comparisonReport(List<Long> planIds) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();

        // Count bounds and per-plan ownership are enforced by the comparison service.
        PlanComparisonResponse comparison = comparisonService.compare(planIds);

        String reportId = newReportId("CMP");
        String generatedAt = formatter.dateTime(Instant.now());

        byte[] content = new ComparisonReportWriter(
                comparison, caller.fullName(), reportId, generatedAt, formatter).write();

        String joinedIds = comparison.plans().stream()
                .map(plan -> String.valueOf(plan.id()))
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        String fileName = fileName("comparison-" + comparison.plans().size() + "-plans", "report");
        record(ReportType.PLAN_COMPARISON, null, joinedIds, fileName, content.length, reportId);

        return new GeneratedReport(fileName, content);
    }

    // -------------------------------------------------------------------- admin log

    /** The generated-report log (FR-RPT-06). Administrators only. */
    @Transactional(readOnly = true)
    public PageResponse<ReportLogResponse> searchLogs(ReportType reportType, Long userId, Pageable pageable) {
        currentUserProvider.assertAdmin();

        return PageResponse.from(
                reportLogRepository.search(reportType, userId, pageable),
                this::toResponse);
    }

    /** Report-type options for the log screen's filter. */
    public List<EnumOption> reportTypeOptions() {
        currentUserProvider.assertAdmin();
        return EnumOption.from(ReportType.class, ReportType::getLabel);
    }

    private ReportLogResponse toResponse(ReportLog entry) {
        return new ReportLogResponse(
                entry.getId(),
                new EnumOption(entry.getReportType().name(), entry.getReportType().getLabel(), null),
                entry.getUser() == null ? null : entry.getUser().getId(),
                entry.getUser() == null ? null : entry.getUser().getFullName(),
                entry.getPlan() == null ? null : entry.getPlan().getId(),
                entry.getPlan() == null ? null : entry.getPlan().getTitle(),
                entry.getPlanIds(),
                entry.getFileName(),
                entry.getFileSizeBytes(),
                entry.getGeneratedAt());
    }

    // ---------------------------------------------------------------------- internals

    /**
     * Records a generated document (FR-RPT-06).
     *
     * <p>Written after the bytes exist. A record of a report that failed to render would be
     * worse than no record: the log is the answer to "did this document come from us?".</p>
     */
    private void record(ReportType type, Long planId, String planIds,
                        String fileName, int sizeBytes, String reportId) {
        Long userId = currentUserProvider.currentUserId();

        ReportLog entry = new ReportLog(
                userId == null ? null : userRepository.getReferenceById(userId),
                planId == null ? null : planRepository.getReferenceById(planId),
                type, planIds, fileName, (long) sizeBytes);
        reportLogRepository.save(entry);

        auditService.record(AuditEventType.REPORT_GENERATED, "ReportLog", entry.getId(),
                "%s generated (%s, %,d bytes)".formatted(type.getLabel(), reportId, sizeBytes));

        log.info("Report {} generated by user {} — {} ({} bytes)", reportId, userId, fileName, sizeBytes);
    }

    /**
     * A human-quotable reference, printed on the document and recorded in the audit trail.
     *
     * <p>Short enough to read out over a phone, random enough not to collide.</p>
     */
    private String newReportId(String prefix) {
        return "UC-%s-%s-%s".formatted(
                prefix,
                LocalDate.now().toString().replace("-", ""),
                UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
    }

    /**
     * A meaningful, safe download filename (FR-RPT-07).
     *
     * <p>The plan's own title, transliterated to ASCII and stripped of anything that is not a
     * letter, digit or hyphen. A title is user input and ends up in a {@code Content-Disposition}
     * header and then on a filesystem: a quote, a newline or a path separator in that position
     * is a header-injection or path-traversal bug, not a cosmetic problem.</p>
     */
    private String fileName(String title, String suffix) {
        String ascii = Normalizer.normalize(title == null ? "plan" : title, Normalizer.Form.NFKD)
                .replaceAll("[^\\p{ASCII}]", "");

        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (slug.isBlank()) {
            slug = "plan";
        }
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-$", "");
        }

        return "urbancanopy-%s-%s-%s.pdf".formatted(slug, suffix, LocalDate.now());
    }
}
