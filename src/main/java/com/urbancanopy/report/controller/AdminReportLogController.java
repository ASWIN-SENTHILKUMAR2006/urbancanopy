package com.urbancanopy.report.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.report.dto.ReportLogResponse;
import com.urbancanopy.report.entity.ReportType;
import com.urbancanopy.report.service.ReportService;
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
 * The log of generated reports (FR-RPT-06).
 *
 * <p>Read-only and append-only, like the audit trail: entries are written as a side effect of
 * producing a document, and an endpoint that could edit or remove one would defeat the purpose
 * of keeping the record at all.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/report-logs")
public class AdminReportLogController {

    private final ReportService reportService;

    public AdminReportLogController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Every generated report, newest first. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReportLogResponse>>> search(
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(required = false) Long userId,
            @PageableDefault(sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
                reportService.searchLogs(reportType, userId, pageable)));
    }

    /** Report types, for the filter. */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<EnumOption>>> reportTypes() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.reportTypeOptions()));
    }
}
