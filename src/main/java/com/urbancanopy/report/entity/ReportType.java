package com.urbancanopy.report.entity;

/** Kinds of downloadable document the system produces. */
public enum ReportType {

    /** Full environmental impact report for a single plantation plan (FR-RPT-02). */
    PLAN_IMPACT("Plan impact report"),

    /** Side-by-side comparison of two to four plans (FR-RPT-05). */
    PLAN_COMPARISON("Plan comparison report");

    private final String label;

    ReportType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
