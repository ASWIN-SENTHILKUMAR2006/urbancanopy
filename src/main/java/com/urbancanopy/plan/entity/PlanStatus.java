package com.urbancanopy.plan.entity;

import java.util.Set;

/**
 * Lifecycle of a plantation plan (FR-PLAN-06).
 *
 * <p>The permitted transitions are declared on the enum itself rather than being scattered
 * through service {@code if} statements, so the state machine can be read in one place and
 * tested in isolation.</p>
 *
 * <pre>
 *   DRAFT ⇄ COMPLETED → ARCHIVED
 *                ↑___________|
 * </pre>
 */
public enum PlanStatus {

    /** Being composed. May be incomplete; its estimate is provisional. */
    DRAFT("Draft", true),

    /** Finalised. A result snapshot is stored and reports may be generated. */
    COMPLETED("Completed", true),

    /** Retained for reference but no longer active. Read-only. */
    ARCHIVED("Archived", false);

    private final String label;
    private final boolean editable;

    PlanStatus(String label, boolean editable) {
        this.label = label;
        this.editable = editable;
    }

    public String getLabel() {
        return label;
    }

    /** Whether a plan in this status may have its details or items changed. */
    public boolean isEditable() {
        return editable;
    }

    /** Statuses this one may move to. */
    public Set<PlanStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> Set.of(COMPLETED, ARCHIVED);
            case COMPLETED -> Set.of(DRAFT, ARCHIVED);
            case ARCHIVED -> Set.of(COMPLETED);
        };
    }

    public boolean canTransitionTo(PlanStatus target) {
        return target != null && allowedTransitions().contains(target);
    }
}
