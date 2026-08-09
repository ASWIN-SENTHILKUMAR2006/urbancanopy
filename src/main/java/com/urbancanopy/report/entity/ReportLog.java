package com.urbancanopy.report.entity;

import com.urbancanopy.common.domain.BaseEntity;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A record that a report was generated (FR-RPT-06, BR-17).
 *
 * <p>Append-only: rows are written once and never updated or deleted, which is what makes
 * the log worth trusting. Both foreign keys are nullable with {@code ON DELETE SET NULL}
 * so the log outlives the plan and the account it refers to — an audit trail that
 * disappears when its subject does is not an audit trail.</p>
 *
 * <p>A comparison report spans several plans, so {@link #plan} is null for those and
 * {@link #planIds} carries the comma-separated identifiers instead. Forcing a single
 * foreign key would have made comparison reports unloggable.</p>
 */
@Entity
@Table(
        name = "report_logs",
        indexes = {
                @Index(name = "idx_report_logs_user_date", columnList = "user_id,generated_at"),
                @Index(name = "idx_report_logs_plan", columnList = "plan_id")
        })
public class ReportLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",
            foreignKey = @ForeignKey(name = "fk_report_logs_user"))
    private User user;

    /** Null for comparison reports, which reference several plans through {@link #planIds}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id",
            foreignKey = @ForeignKey(name = "fk_report_logs_plan"))
    private PlantationPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    /** Comma-separated plan identifiers, used by comparison reports. */
    @Column(name = "plan_ids", length = 255)
    private String planIds;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ReportLog() {
        // Required by JPA.
    }

    public ReportLog(User user, PlantationPlan plan, ReportType reportType,
                     String planIds, String fileName, Long fileSizeBytes) {
        this.user = user;
        this.plan = plan;
        this.reportType = reportType;
        this.planIds = planIds;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.generatedAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public PlantationPlan getPlan() {
        return plan;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public String getPlanIds() {
        return planIds;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
