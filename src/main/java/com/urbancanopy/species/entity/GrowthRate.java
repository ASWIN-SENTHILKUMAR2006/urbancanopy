package com.urbancanopy.species.entity;

/**
 * How quickly a species approaches its mature size.
 *
 * <p>This is the input that drives the maturity ramp in the projection: a fast-growing
 * species reaches full sequestration performance years before a slow-growing one, which
 * is why a 10-year projection for neem and for banyan look nothing alike.</p>
 *
 * <p><strong>The years-to-maturity figures themselves are not held here.</strong> They
 * live in the {@code environmental_coefficients} table under the keys
 * {@code calc.maturity.years.slow|moderate|fast}, so an administrator can revise them
 * against new literature without a code change (BR-15). This enum only names the
 * category and knows which coefficient key it maps to.</p>
 */
public enum GrowthRate {

    SLOW("Slow", "calc.maturity.years.slow"),
    MODERATE("Moderate", "calc.maturity.years.moderate"),
    FAST("Fast", "calc.maturity.years.fast");

    private final String label;
    private final String maturityYearsCoefficientKey;

    GrowthRate(String label, String maturityYearsCoefficientKey) {
        this.label = label;
        this.maturityYearsCoefficientKey = maturityYearsCoefficientKey;
    }

    public String getLabel() {
        return label;
    }

    /** Coefficient key holding the years this growth rate takes to reach maturity. */
    public String getMaturityYearsCoefficientKey() {
        return maturityYearsCoefficientKey;
    }
}
