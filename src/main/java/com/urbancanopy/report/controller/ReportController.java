package com.urbancanopy.report.controller;

import com.urbancanopy.report.service.GeneratedReport;
import com.urbancanopy.report.service.ReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Report downloads (FR-RPT-01, FR-RPT-05, FR-RPT-07, FR-RPT-08).
 *
 * <p>These are the only endpoints in the application that do not return the {@code ApiResponse}
 * envelope, and they cannot: the body is a PDF, not JSON. The client therefore treats them as
 * downloads rather than API calls — which is why {@code api.js} has no part in them and the
 * pages link to these URLs directly.</p>
 *
 * <p>Ownership is enforced in the service, through the same estimate and comparison paths the
 * screens use. There is no arrangement of these parameters that returns a plan the caller could
 * not already open (FR-RPT-08).</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** The full impact report for one plan. */
    @GetMapping("/plans/{planId}/pdf")
    public ResponseEntity<Resource> planReport(@PathVariable Long planId) {
        return download(reportService.planReport(planId));
    }

    /** A comparison of two to four plans. */
    @GetMapping("/comparison/pdf")
    public ResponseEntity<Resource> comparisonReport(@RequestParam List<Long> planIds) {
        return download(reportService.comparisonReport(planIds));
    }

    /**
     * Streams a rendered document.
     *
     * <p>The filename goes through {@link ContentDisposition} rather than into a hand-built
     * header string, which is where a title containing a quote or a newline turns into a
     * header-injection bug. No charset is supplied because the service slugifies the filename to
     * ASCII by construction: asking for UTF-8 encoding would emit an RFC 2047 encoded-word
     * ({@code =?UTF-8?Q?…?=}) in the plain {@code filename} parameter — a mail convention that
     * some HTTP clients show to the user verbatim.</p>
     *
     * <p>{@code Content-Length} is set because the whole document is already in memory, so the
     * browser can show real progress rather than an indeterminate spinner. {@code no-store}
     * because a report reflects the plan at one moment and a cached copy would quietly become
     * a different document from the one the URL now describes.</p>
     */
    private ResponseEntity<Resource> download(GeneratedReport report) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(report.fileName())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(report.sizeBytes())
                .body(new ByteArrayResource(report.content()));
    }
}
