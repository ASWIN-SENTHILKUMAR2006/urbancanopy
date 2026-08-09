package com.urbancanopy.user.entity;

import com.urbancanopy.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * An application account.
 *
 * <p>The password is held only as a BCrypt hash and is never exposed by any DTO, log
 * statement or {@code toString} (FR-AUTH-02, BR-02). {@link #passwordHash} is deliberately
 * not readable through a conventional getter name that a serialiser would pick up — the
 * user DTOs simply do not carry the field.</p>
 *
 * <p>The lockout counters exist from Phase 6 so the schema is stable, and are exercised by
 * the authentication logic added in Phase 12 (FR-AUTH-11). Storing them now costs nothing;
 * adding a column later to a table with data costs a migration.</p>
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"),
        indexes = @Index(name = "idx_users_role_enabled", columnList = "role,enabled"))
public class User extends AuditableEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** Unique login identifier, always stored lower-cased (BR-01). */
    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "organisation", length = 150)
    private String organisation;

    @Column(name = "designation", length = 100)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.ROLE_USER;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
        // Required by JPA.
    }

    public User(String fullName, String email, String passwordHash, Role role) {
        this.fullName = fullName;
        this.email = normaliseEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** Emails are compared case-insensitively, so they are stored case-folded (BR-01). */
    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }

    /** {@code true} while a temporary lockout from failed logins is in force. */
    public boolean isTemporarilyLocked() {
        return lockExpiresAt != null && lockExpiresAt.isAfter(Instant.now());
    }

    /** {@code true} when the account may authenticate right now. */
    public boolean isLoginPermitted() {
        return enabled && !isTemporarilyLocked();
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockExpiresAt = null;
        this.lastLoginAt = Instant.now();
    }

    public void recordFailedLogin(int maxAttempts, java.time.Duration lockDuration) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockExpiresAt = Instant.now().plus(lockDuration);
        }
    }

    // ------------------------------------------------------------------ accessors

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normaliseEmail(email);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getOrganisation() {
        return organisation;
    }

    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
