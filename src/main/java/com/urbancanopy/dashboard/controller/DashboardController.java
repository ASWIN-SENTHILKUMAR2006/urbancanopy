package com.urbancanopy.dashboard.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.dashboard.dto.ChartData;
import com.urbancanopy.dashboard.dto.DashboardSummary;
import com.urbancanopy.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard data (FR-DASH-01 … FR-DASH-08).
 *
 * <p>Everything arrives as JSON so the charts render without a page reload, and every response
 * is scoped to the caller inside the query rather than filtered afterwards — there is no
 * parameter here that could widen the scope, because there is no parameter at all.</p>
 *
 * <p>The charts are separate requests from the summary. The KPI row is what the reader looks
 * at first and it depends on nothing else, so it should not wait behind four chart
 * aggregations; a chart that fails renders its own error state without taking the headline
 * figures down with it.</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** KPI cards, recent activity, and — for an administrator — the system-wide block. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary()));
    }

    /**
     * One chart's data.
     *
     * @param chartKey {@code co2-by-species}, {@code pollutant-breakdown},
     *                 {@code co2-projection} or {@code plans-by-purpose}. An unrecognised key
     *                 is a 404 rather than an empty chart, so a client typo reads as the
     *                 mistake it is instead of as "you have no data".
     */
    @GetMapping("/charts/{chartKey}")
    public ResponseEntity<ApiResponse<ChartData>> chart(@PathVariable String chartKey) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.chart(chartKey)));
    }
}
