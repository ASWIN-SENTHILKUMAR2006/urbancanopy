package com.urbancanopy.audit.entity;

/**
 * Events worth recording in the audit trail (FR-ADM-07).
 *
 * <p>Scoped deliberately: security decisions and changes to shared reference data. Ordinary
 * activity — a planner editing their own draft — is not recorded, because an audit log that
 * captures everything captures nothing an administrator can actually read.</p>
 */
public enum AuditEventType {

    LOGIN_SUCCEEDED("Login succeeded"),
    LOGIN_FAILED("Login failed"),
    ACCOUNT_REGISTERED("Account registered"),
    ACCOUNT_ENABLED("Account enabled"),
    ACCOUNT_DISABLED("Account disabled"),
    ROLE_CHANGED("Role changed"),
    PASSWORD_CHANGED("Password changed"),

    SPECIES_CREATED("Species created"),
    SPECIES_UPDATED("Species updated"),
    SPECIES_DEACTIVATED("Species deactivated"),
    SPECIES_ACTIVATED("Species activated"),
    SPECIES_DELETED("Species deleted"),

    COEFFICIENT_CHANGED("Coefficient changed"),
    COEFFICIENT_RESTORED("Coefficient restored to default"),

    REPORT_GENERATED("Report generated"),

    /** An administrator viewed a plan they do not own (FR-PLAN-11). */
    ADMIN_VIEWED_PLAN("Administrator viewed a plan");

    private final String label;

    AuditEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
