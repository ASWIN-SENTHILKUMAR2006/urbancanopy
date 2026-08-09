package com.urbancanopy.species.entity;

import com.urbancanopy.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * A tree species and the curated scientific reference values that describe its
 * environmental performance.
 *
 * <p>This entity is the scientific foundation of the entire application: every estimate
 * the system produces is one of these per-tree rates multiplied by a quantity. Three
 * consequences follow, and each is enforced here.</p>
 *
 * <ol>
 *   <li><strong>Every rate carries its unit in its field name.</strong> A field called
 *       {@code co2} would be ambiguous between kg and tonnes, and per tree, per hectare
 *       or per year. {@code co2SequestrationKgPerYear} cannot be misread.</li>
 *   <li><strong>Every species must cite its source</strong> ({@link #sourceCitation},
 *       BR-19). A reference value with no provenance is an assertion, not science, and
 *       the citation is reproduced in the PDF methodology appendix.</li>
 *   <li><strong>A species is deactivated, never deleted, once a plan references it</strong>
 *       ({@link #active}, BR-18). Deleting it would silently change or break every
 *       historical plan that depended on it.</li>
 * </ol>
 *
 * <p>Carbon and oxygen rates are stored in kilograms per tree per year; pollutant removal
 * rates are stored in grams per tree per year, because published figures for particulates
 * and gases are an order of magnitude smaller and grams keep them legible.</p>
 */
@Entity
@Table(
        name = "tree_species",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_species_common_name", columnNames = "common_name"),
                @UniqueConstraint(name = "uq_species_scientific_name", columnNames = "scientific_name")
        },
        indexes = {
                @Index(name = "idx_species_active", columnList = "active"),
                @Index(name = "idx_species_growth_climate", columnList = "growth_rate,climate_zone"),
                @Index(name = "idx_species_co2", columnList = "co2_sequestration_kg_year")
        })
public class TreeSpecies extends AuditableEntity {

    // ------------------------------------------------------------------------ identity

    @Column(name = "common_name", nullable = false, length = 120)
    private String commonName;

    @Column(name = "scientific_name", nullable = false, length = 160)
    private String scientificName;

    @Column(name = "family", length = 100)
    private String family;

    @Column(name = "local_name", length = 120)
    private String localName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Filename of an uploaded image, relative to the configured upload directory. */
    @Column(name = "image_path", length = 255)
    private String imagePath;

    /** External image URL, used when no file has been uploaded. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // ------------------------------------------------- environmental performance (FR-SPEC-03)

    /** Annual carbon dioxide sequestered by one mature tree, in kilograms per year. */
    @Column(name = "co2_sequestration_kg_year", nullable = false, precision = 10, scale = 3)
    private BigDecimal co2SequestrationKgPerYear;

    /** Annual oxygen released by one mature tree, in kilograms per year. */
    @Column(name = "oxygen_production_kg_year", nullable = false, precision = 10, scale = 3)
    private BigDecimal oxygenProductionKgPerYear;

    /** Annual PM2.5 removed by one mature tree, in grams per year. */
    @Column(name = "pm25_removal_g_year", nullable = false, precision = 10, scale = 3)
    private BigDecimal pm25RemovalGramsPerYear;

    /** Annual PM10 removed by one mature tree, in grams per year. */
    @Column(name = "pm10_removal_g_year", nullable = false, precision = 10, scale = 3)
    private BigDecimal pm10RemovalGramsPerYear;

    /** Annual nitrogen dioxide removed by one mature tree, in grams per year. */
    @Column(name = "no2_removal_g_year", nullable = false, precision = 10, scale = 3)
    private BigDecimal no2RemovalGramsPerYear;

    /** Annual sulphur dioxide removed by one mature tree, in grams per year. */
    @Column(name = "so2_removal_g_year", nullable = false, precision = 10, scale = 3)
    private BigDecimal so2RemovalGramsPerYear;

    /** Annual carbon monoxide removed by one mature tree, in grams per year. */
    @Column(name = "co_removal_g_year", nullable = false, precision = 10, scale = 3)
    private BigDecimal coRemovalGramsPerYear;

    // ------------------------------------------------------ horticultural profile (FR-SPEC-04)

    @Enumerated(EnumType.STRING)
    @Column(name = "growth_rate", nullable = false, length = 20)
    private GrowthRate growthRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "water_requirement", nullable = false, length = 20)
    private WaterRequirement waterRequirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "climate_zone", nullable = false, length = 30)
    private ClimateZone climateZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "soil_type", nullable = false, length = 30)
    private SoilType soilType;

    /** Radius of the canopy at maturity, in metres. Drives canopy area (FR-CALC-04). */
    @Column(name = "canopy_radius_m", nullable = false, precision = 6, scale = 2)
    private BigDecimal canopyRadiusMetres;

    @Column(name = "mature_height_m", precision = 6, scale = 2)
    private BigDecimal matureHeightMetres;

    @Column(name = "life_span_years", nullable = false)
    private Integer lifeSpanYears;

    /**
     * Recommended centre-to-centre distance between trees, in metres.
     * Drives the spatial feasibility assessment (FR-CALC-05).
     */
    @Column(name = "spacing_m", nullable = false, precision = 6, scale = 2)
    private BigDecimal spacingMetres;

    @Column(name = "native_species", nullable = false)
    private boolean nativeSpecies = true;

    // -------------------------------------------------------------- provenance and lifecycle

    /** Literature source for the environmental values above (BR-19). */
    @Column(name = "source_citation", nullable = false, length = 500)
    private String sourceCitation;

    /**
     * Whether the species may be selected for new plans. Existing plan items continue to
     * reference and compute from an inactive species (BR-18, FR-SPEC-12).
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected TreeSpecies() {
        // Required by JPA.
    }

    // -------------------------------------------------------------------- derived helpers

    /**
     * Total annual pollutant removal for one mature tree, in grams per year — the sum of
     * all five pollutant rates.
     */
    public BigDecimal getTotalPollutantRemovalGramsPerYear() {
        return pm25RemovalGramsPerYear
                .add(pm10RemovalGramsPerYear)
                .add(no2RemovalGramsPerYear)
                .add(so2RemovalGramsPerYear)
                .add(coRemovalGramsPerYear);
    }

    /** Display name combining common and scientific names. */
    public String getDisplayName() {
        return commonName + " (" + scientificName + ")";
    }

    // ------------------------------------------------------------------ accessors

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(String scientificName) {
        this.scientificName = scientificName;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getCo2SequestrationKgPerYear() {
        return co2SequestrationKgPerYear;
    }

    public void setCo2SequestrationKgPerYear(BigDecimal co2SequestrationKgPerYear) {
        this.co2SequestrationKgPerYear = co2SequestrationKgPerYear;
    }

    public BigDecimal getOxygenProductionKgPerYear() {
        return oxygenProductionKgPerYear;
    }

    public void setOxygenProductionKgPerYear(BigDecimal oxygenProductionKgPerYear) {
        this.oxygenProductionKgPerYear = oxygenProductionKgPerYear;
    }

    public BigDecimal getPm25RemovalGramsPerYear() {
        return pm25RemovalGramsPerYear;
    }

    public void setPm25RemovalGramsPerYear(BigDecimal pm25RemovalGramsPerYear) {
        this.pm25RemovalGramsPerYear = pm25RemovalGramsPerYear;
    }

    public BigDecimal getPm10RemovalGramsPerYear() {
        return pm10RemovalGramsPerYear;
    }

    public void setPm10RemovalGramsPerYear(BigDecimal pm10RemovalGramsPerYear) {
        this.pm10RemovalGramsPerYear = pm10RemovalGramsPerYear;
    }

    public BigDecimal getNo2RemovalGramsPerYear() {
        return no2RemovalGramsPerYear;
    }

    public void setNo2RemovalGramsPerYear(BigDecimal no2RemovalGramsPerYear) {
        this.no2RemovalGramsPerYear = no2RemovalGramsPerYear;
    }

    public BigDecimal getSo2RemovalGramsPerYear() {
        return so2RemovalGramsPerYear;
    }

    public void setSo2RemovalGramsPerYear(BigDecimal so2RemovalGramsPerYear) {
        this.so2RemovalGramsPerYear = so2RemovalGramsPerYear;
    }

    public BigDecimal getCoRemovalGramsPerYear() {
        return coRemovalGramsPerYear;
    }

    public void setCoRemovalGramsPerYear(BigDecimal coRemovalGramsPerYear) {
        this.coRemovalGramsPerYear = coRemovalGramsPerYear;
    }

    public GrowthRate getGrowthRate() {
        return growthRate;
    }

    public void setGrowthRate(GrowthRate growthRate) {
        this.growthRate = growthRate;
    }

    public WaterRequirement getWaterRequirement() {
        return waterRequirement;
    }

    public void setWaterRequirement(WaterRequirement waterRequirement) {
        this.waterRequirement = waterRequirement;
    }

    public ClimateZone getClimateZone() {
        return climateZone;
    }

    public void setClimateZone(ClimateZone climateZone) {
        this.climateZone = climateZone;
    }

    public SoilType getSoilType() {
        return soilType;
    }

    public void setSoilType(SoilType soilType) {
        this.soilType = soilType;
    }

    public BigDecimal getCanopyRadiusMetres() {
        return canopyRadiusMetres;
    }

    public void setCanopyRadiusMetres(BigDecimal canopyRadiusMetres) {
        this.canopyRadiusMetres = canopyRadiusMetres;
    }

    public BigDecimal getMatureHeightMetres() {
        return matureHeightMetres;
    }

    public void setMatureHeightMetres(BigDecimal matureHeightMetres) {
        this.matureHeightMetres = matureHeightMetres;
    }

    public Integer getLifeSpanYears() {
        return lifeSpanYears;
    }

    public void setLifeSpanYears(Integer lifeSpanYears) {
        this.lifeSpanYears = lifeSpanYears;
    }

    public BigDecimal getSpacingMetres() {
        return spacingMetres;
    }

    public void setSpacingMetres(BigDecimal spacingMetres) {
        this.spacingMetres = spacingMetres;
    }

    public boolean isNativeSpecies() {
        return nativeSpecies;
    }

    public void setNativeSpecies(boolean nativeSpecies) {
        this.nativeSpecies = nativeSpecies;
    }

    public String getSourceCitation() {
        return sourceCitation;
    }

    public void setSourceCitation(String sourceCitation) {
        this.sourceCitation = sourceCitation;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
