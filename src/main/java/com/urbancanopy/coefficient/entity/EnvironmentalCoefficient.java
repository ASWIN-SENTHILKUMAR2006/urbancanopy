package com.urbancanopy.coefficient.entity;

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
 * One tunable scientific constant used by the calculation engine.
 *
 * <p>This entity is what turns "never hardcode values" from a coding aspiration into an
 * enforceable property of the system (BR-15). Survival rates, maturity periods,
 * feasibility thresholds, equivalence factors and scoring weights are all rows in this
 * table rather than literals in Java, which means:</p>
 *
 * <ul>
 *   <li>an administrator can revise a figure against newer literature without a redeploy
 *       (FR-ADM-04, FR-ADM-05);</li>
 *   <li>every constant carries its own {@link #minValue}/{@link #maxValue} range, so an
 *       implausible edit is rejected rather than silently corrupting every estimate
 *       (FR-ADM-05);</li>
 *   <li>every constant carries its {@link #source}, so the methodology appendix of a
 *       report can cite it;</li>
 *   <li>{@link #defaultValue} is retained permanently, so a mistaken change can always be
 *       undone (FR-ADM-06).</li>
 * </ul>
 *
 * <p>Adding a new equivalence — "this offsets N domestic flights" — is one row in this
 * table and no Java at all.</p>
 */
@Entity
@Table(
        name = "environmental_coefficients",
        uniqueConstraints = @UniqueConstraint(name = "uq_coefficient_key", columnNames = "coefficient_key"),
        indexes = @Index(name = "idx_coefficient_category", columnList = "category"))
public class EnvironmentalCoefficient extends AuditableEntity {

    /** Stable programmatic identifier, e.g. {@code calc.survival.rate}. Never changes. */
    @Column(name = "coefficient_key", nullable = false, length = 80)
    private String coefficientKey;

    /** Human-readable name shown in the admin screen. */
    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "coefficient_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal value;

    /** The seeded value, retained so the coefficient can always be restored. */
    @Column(name = "default_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal defaultValue;

    @Column(name = "min_value", precision = 18, scale = 6)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 18, scale = 6)
    private BigDecimal maxValue;

    /** Unit of measure, e.g. {@code kg CO2/km}, {@code years}, {@code ratio}. */
    @Column(name = "unit", length = 40)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private CoefficientCategory category;

    @Column(name = "description", length = 500)
    private String description;

    /** Literature or standard the value is taken from. */
    @Column(name = "source", length = 500)
    private String source;

    protected EnvironmentalCoefficient() {
        // Required by JPA.
    }

    /** {@code true} when {@code candidate} lies inside this coefficient's permitted range. */
    public boolean isWithinRange(BigDecimal candidate) {
        if (candidate == null) {
            return false;
        }
        if (minValue != null && candidate.compareTo(minValue) < 0) {
            return false;
        }
        return maxValue == null || candidate.compareTo(maxValue) <= 0;
    }

    /** Human-readable description of the permitted range, for error messages. */
    public String describeRange() {
        if (minValue == null && maxValue == null) {
            return "unrestricted";
        }
        if (minValue == null) {
            return "at most " + maxValue.stripTrailingZeros().toPlainString();
        }
        if (maxValue == null) {
            return "at least " + minValue.stripTrailingZeros().toPlainString();
        }
        return minValue.stripTrailingZeros().toPlainString()
                + " to " + maxValue.stripTrailingZeros().toPlainString();
    }

    /** {@code true} when the current value differs from the seeded default. */
    public boolean isModified() {
        return value != null && defaultValue != null && value.compareTo(defaultValue) != 0;
    }

    public void restoreDefault() {
        this.value = this.defaultValue;
    }

    // ------------------------------------------------------------------ accessors

    public String getCoefficientKey() {
        return coefficientKey;
    }

    public void setCoefficientKey(String coefficientKey) {
        this.coefficientKey = coefficientKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(BigDecimal defaultValue) {
        this.defaultValue = defaultValue;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public void setMinValue(BigDecimal minValue) {
        this.minValue = minValue;
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(BigDecimal maxValue) {
        this.maxValue = maxValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public CoefficientCategory getCategory() {
        return category;
    }

    public void setCategory(CoefficientCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
