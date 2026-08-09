package com.urbancanopy.user.entity;

/**
 * Authorities recognised by the application.
 *
 * <p>Stored as a single column rather than a {@code roles} / {@code user_roles} pair: the
 * role set is closed and fixed, a user holds exactly one, and {@link #ROLE_ADMIN}
 * subsumes {@link #ROLE_USER}. A join table would remove no repeating group and eliminate
 * no transitive dependency — it would only add two joins to every authorisation check.
 * Should roles ever become open-ended, introducing a join table is a purely additive
 * migration.</p>
 *
 * <p>Names carry the {@code ROLE_} prefix so they map directly onto Spring Security
 * authorities when authentication is introduced in Phase 12.</p>
 */
public enum Role {

    /** A planner: creates and manages their own plantation plans. */
    ROLE_USER("Planner"),

    /** An administrator: curates reference data and manages accounts. Implies planner. */
    ROLE_ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** {@code true} when this role grants administrator privileges. */
    public boolean isAdmin() {
        return this == ROLE_ADMIN;
    }
}
