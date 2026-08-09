package com.urbancanopy.plan.entity;

import com.urbancanopy.calculation.model.FeasibilityVerdict;
import com.urbancanopy.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A frozen copy of a plan's computed result (FR-CALC-14).
 *
 * <p>Every column here is derivable from the plan's items and the coefficient registry.
 * The table exists precisely so that it does <em>not</em> have to be re-derived. Without
 * it, an administrator revising {@code calc.survival.rate} would silently rewrite every
 * report the system has ever produced, and a figure quoted in a March document would no
 * longer reconcile in December.</p>
 *
 * <p>{@link #coefficientSnapshot} stores the exact constants in force at the moment of
 * computation as JSON, so a historical result records not only <em>what</em> was computed
 * but <em>why</em>. {@link #engineVersion} records which implementation produced it, so a
 * later change to a formula is also traceable.</p>
 *
 * <p>Extends {@link BaseEntity} rather than {@code AuditableEntity}: a snapshot is written
 * once and replaced wholesale, never edited, so {@code updated_by} would always be a lie.</p>
 */
@Entity
@Table(
        name = "plan_result_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uq_snapshot_plan", columnNames = "plan_id"))
public class PlanResultSnapshot extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_snapshot_plan"))
    private PlantationPlan plan;

    @Column(name = "total_trees", nullable = false)
    private int totalTrees;

    /** Trees adjusted for survival and maturity — the basis of every rate below. */
    @Column(name = "effective_trees", nullable = false, precision = 14, scale = 2)
    private BigDecimal effectiveTrees;

    @Column(name = "annual_co2_kg", nullable = false, precision = 16, scale = 3)
    private BigDecimal annualCo2Kg;

    @Column(name = "annual_o2_kg", nullable = false, precision = 16, scale = 3)
    private BigDecimal annualO2Kg;

    @Column(name = "annual_pm25_g", nullable = false, precision = 16, scale = 3)
    private BigDecimal annualPm25Grams;

    @Column(name = "annual_pm10_g", nullable = false, precision = 16, scale = 3)
    private BigDecimal annualPm10Grams;

    @Column(name = "annual_no2_g", nullable = false, precision = 16, scale = 3)
    private BigDecimal annualNo2Grams;

    @Column(name = "annual_so2_g", nullable = false, precision = 16, scale = 3)
    private BigDecimal annualSo2Grams;

    @Column(name = "annual_co_g", nullable = false, precision = 16, scale = 3)
    private BigDecimal annualCoGrams;

    @Column(name = "total_pollutant_g", nullable = false, precision = 16, scale = 3)
    private BigDecimal totalPollutantGrams;

    @Column(name = "canopy_area_sqm", nullable = false, precision = 16, scale = 2)
    private BigDecimal canopyAreaSquareMetres;

    /** Capped at 100 for display; the uncapped ratio drives the feasibility verdict. */
    @Column(name = "canopy_coverage_percent", precision = 7, scale = 2)
    private BigDecimal canopyCoveragePercent;

    @Column(name = "required_area_sqm", nullable = false, precision = 16, scale = 2)
    private BigDecimal requiredAreaSquareMetres;

    @Column(name = "space_utilisation_percent", precision = 9, scale = 2)
    private BigDecimal spaceUtilisationPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "feasibility", nullable = false, length = 20)
    private FeasibilityVerdict feasibility;

    /** Shannon–Wiener index over the species mix (FR-CALC-11). */
    @Column(name = "diversity_index", precision = 6, scale = 4)
    private BigDecimal diversityIndex;

    @Column(name = "impact_score", precision = 5, scale = 2)
    private BigDecimal impactScore;

    /** Cumulative CO₂ over the plan's projection horizon, in kilograms. */
    @Column(name = "horizon_co2_kg", nullable = false, precision = 18, scale = 3)
    private BigDecimal horizonCo2Kg;

    /** The coefficient set in force when this result was produced, as JSON. */
    @Column(name = "coefficient_snapshot", columnDefinition = "JSON")
    private String coefficientSnapshot;

    @Column(name = "engine_version", nullable = false, length = 20)
    private String engineVersion;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected PlanResultSnapshot() {
        // Required by JPA.
    }

    public PlanResultSnapshot(PlantationPlan plan) {
        this.plan = plan;
        this.computedAt = Instant.now();
    }

    // ------------------------------------------------------------------ accessors

    public PlantationPlan getPlan() {
        return plan;
    }

    public int getTotalTrees() {
        return totalTrees;
    }

    public void setTotalTrees(int totalTrees) {
        this.totalTrees = totalTrees;
    }

    public BigDecimal getEffectiveTrees() {
        return effectiveTrees;
    }

    public void setEffectiveTrees(BigDecimal effectiveTrees) {
        this.effectiveTrees = effectiveTrees;
    }

    public BigDecimal getAnnualCo2Kg() {
        return annualCo2Kg;
    }

    public void setAnnualCo2Kg(BigDecimal annualCo2Kg) {
        this.annualCo2Kg = annualCo2Kg;
    }

    public BigDecimal getAnnualO2Kg() {
        return annualO2Kg;
    }

    public void setAnnualO2Kg(BigDecimal annualO2Kg) {
        this.annualO2Kg = annualO2Kg;
    }

    public BigDecimal getAnnualPm25Grams() {
        return annualPm25Grams;
    }

    public void setAnnualPm25Grams(BigDecimal annualPm25Grams) {
        this.annualPm25Grams = annualPm25Grams;
    }

    public BigDecimal getAnnualPm10Grams() {
        return annualPm10Grams;
    }

    public void setAnnualPm10Grams(BigDecimal annualPm10Grams) {
        this.annualPm10Grams = annualPm10Grams;
    }

    public BigDecimal getAnnualNo2Grams() {
        return annualNo2Grams;
    }

    public void setAnnualNo2Grams(BigDecimal annualNo2Grams) {
        this.annualNo2Grams = annualNo2Grams;
    }

    public BigDecimal getAnnualSo2Grams() {
        return annualSo2Grams;
    }

    public void setAnnualSo2Grams(BigDecimal annualSo2Grams) {
        this.annualSo2Grams = annualSo2Grams;
    }

    public BigDecimal getAnnualCoGrams() {
        return annualCoGrams;
    }

    public void setAnnualCoGrams(BigDecimal annualCoGrams) {
        this.annualCoGrams = annualCoGrams;
    }

    public BigDecimal getTotalPollutantGrams() {
        return totalPollutantGrams;
    }

    public void setTotalPollutantGrams(BigDecimal totalPollutantGrams) {
        this.totalPollutantGrams = totalPollutantGrams;
    }

    public BigDecimal getCanopyAreaSquareMetres() {
        return canopyAreaSquareMetres;
    }

    public void setCanopyAreaSquareMetres(BigDecimal canopyAreaSquareMetres) {
        this.canopyAreaSquareMetres = canopyAreaSquareMetres;
    }

    public BigDecimal getCanopyCoveragePercent() {
        return canopyCoveragePercent;
    }

    public void setCanopyCoveragePercent(BigDecimal canopyCoveragePercent) {
        this.canopyCoveragePercent = canopyCoveragePercent;
    }

    public BigDecimal getRequiredAreaSquareMetres() {
        return requiredAreaSquareMetres;
    }

    public void setRequiredAreaSquareMetres(BigDecimal requiredAreaSquareMetres) {
        this.requiredAreaSquareMetres = requiredAreaSquareMetres;
    }

    public BigDecimal getSpaceUtilisationPercent() {
        return spaceUtilisationPercent;
    }

    public void setSpaceUtilisationPercent(BigDecimal spaceUtilisationPercent) {
        this.spaceUtilisationPercent = spaceUtilisationPercent;
    }

    public FeasibilityVerdict getFeasibility() {
        return feasibility;
    }

    public void setFeasibility(FeasibilityVerdict feasibility) {
        this.feasibility = feasibility;
    }

    public BigDecimal getDiversityIndex() {
        return diversityIndex;
    }

    public void setDiversityIndex(BigDecimal diversityIndex) {
        this.diversityIndex = diversityIndex;
    }

    public BigDecimal getImpactScore() {
        return impactScore;
    }

    public void setImpactScore(BigDecimal impactScore) {
        this.impactScore = impactScore;
    }

    public BigDecimal getHorizonCo2Kg() {
        return horizonCo2Kg;
    }

    public void setHorizonCo2Kg(BigDecimal horizonCo2Kg) {
        this.horizonCo2Kg = horizonCo2Kg;
    }

    public String getCoefficientSnapshot() {
        return coefficientSnapshot;
    }

    public void setCoefficientSnapshot(String coefficientSnapshot) {
        this.coefficientSnapshot = coefficientSnapshot;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
