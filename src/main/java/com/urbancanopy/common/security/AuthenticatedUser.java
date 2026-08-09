package com.urbancanopy.common.security;

/**
 * The caller's identity, as the service layer sees it.
 *
 * <p>Deliberately a small immutable record rather than a JPA entity or a Spring Security
 * {@code UserDetails}: services must be able to reason about "who is asking?" without
 * depending on either persistence or a security framework. When Spring Security arrives
 * in Phase 12, only the {@link CurrentUserProvider} implementation changes — this type,
 * and every service that consumes it, stays exactly as it is.</p>
 *
 * @param id       persistent identifier of the user
 * @param email    unique login identifier
 * @param fullName display name
 * @param admin    whether the caller holds {@code ROLE_ADMIN}
 */
public record AuthenticatedUser(Long id, String email, String fullName, boolean admin) {

    /** {@code true} when this user owns the entity created by {@code ownerId}. */
    public boolean owns(Long ownerId) {
        return ownerId != null && ownerId.equals(id);
    }

    /** {@code true} when this user may act on data owned by {@code ownerId}. */
    public boolean canAccessDataOf(Long ownerId) {
        return admin || owns(ownerId);
    }
}
