package com.urbancanopy.calculation.model;

/**
 * How well the chosen number of trees fits the available land (FR-CALC-05).
 *
 * <p>This verdict is what makes the application a decision-support system rather than a
 * calculator: it answers "can this actually be planted here?", which is the question a
 * planner asks immediately after seeing a carbon figure.</p>
 *
 * <p>All four verdicts are <strong>advisory</strong>. An overcrowded plan is warned about
 * in the strongest terms the interface has, and then saved anyway (BR-13) — the system
 * informs, the planner decides.</p>
 */
public enum FeasibilityVerdict {

    UNDER_UTILISED("Under-utilised", "info",
            "There is substantial unused space. Consider planting more trees or adding species."),

    OPTIMAL("Optimal", "success",
            "The planting density fits the available land well."),

    TIGHT("Tight", "warning",
            "The site is close to capacity. Growth may be constrained and maintenance access limited."),

    OVERCROWDED("Overcrowded", "danger",
            "The recommended spacing cannot be met. Trees will compete for light, water and "
                    + "nutrients, and the estimated benefits are unlikely to be realised."),

    /** Reported when land area is unknown or zero — the question cannot be answered. */
    NOT_APPLICABLE("Not applicable", "muted",
            "No land area was supplied, so spatial feasibility could not be assessed.");

    private final String label;
    private final String severity;
    private final String guidance;

    FeasibilityVerdict(String label, String severity, String guidance) {
        this.label = label;
        this.severity = severity;
        this.guidance = guidance;
    }

    public String getLabel() {
        return label;
    }

    /** Severity token consumed by the interface to pick a colour and icon. */
    public String getSeverity() {
        return severity;
    }

    public String getGuidance() {
        return guidance;
    }
}
