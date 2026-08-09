package com.urbancanopy.common.security;

import com.urbancanopy.common.exception.ForbiddenOperationException;
import com.urbancanopy.common.exception.UnauthenticatedException;

import java.util.Optional;

/**
 * The single question the service layer is allowed to ask about the caller.
 *
 * <p>This interface is the seam that lets authentication be built last (Phase 12) without
 * any service being rewritten. Services never touch {@code SecurityContextHolder}, never
 * read a session, and never see an HTTP request; they ask this abstraction and get an
 * {@link AuthenticatedUser}.</p>
 *
 * <table border="1">
 *   <caption>Implementations by phase</caption>
 *   <tr><th>Phase</th><th>Implementation</th><th>Source of identity</th></tr>
 *   <tr><td>5 – 11</td><td>{@link DevCurrentUserProvider}</td><td>seeded demo account</td></tr>
 *   <tr><td>12 →</td><td>{@code SecurityContextUserProvider}</td><td>authenticated principal</td></tr>
 * </table>
 *
 * <p><strong>Ownership rules are not deferred.</strong> {@link #assertCanAccess} is domain
 * logic and is enforced from Phase 8 onward, satisfying FR-PLAN-10 and BR-06 regardless of
 * how authentication happens.</p>
 */
public interface CurrentUserProvider {

    /** The caller, if one can be identified. */
    Optional<AuthenticatedUser> currentUser();

    /**
     * The caller, or a failure.
     *
     * @throws UnauthenticatedException when no caller can be identified
     */
    default AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(
                () -> new UnauthenticatedException("Authentication is required for this operation."));
    }

    /** {@code true} when the caller holds {@code ROLE_ADMIN}. */
    default boolean isAdmin() {
        return currentUser().map(AuthenticatedUser::admin).orElse(false);
    }

    /** The caller's id, or {@code null} when anonymous. */
    default Long currentUserId() {
        return currentUser().map(AuthenticatedUser::id).orElse(null);
    }

    /**
     * Enforces that the caller may act on data owned by {@code ownerId}.
     *
     * <p>An administrator passes; the owner passes; anyone else is refused. The refusal is
     * an explicit 403 rather than a 404, because pretending a resource does not exist
     * hides genuine bugs from us as surely as it hides data from an attacker.</p>
     *
     * @param ownerId      the id of the user who owns the resource
     * @param resourceName human-readable resource name, used in the message
     * @throws ForbiddenOperationException when the caller may not access the resource
     */
    default void assertCanAccess(Long ownerId, String resourceName) {
        AuthenticatedUser caller = requireCurrentUser();
        if (!caller.canAccessDataOf(ownerId)) {
            throw new ForbiddenOperationException(
                    "You do not have permission to access this " + resourceName + ".");
        }
    }

    /**
     * Enforces that the caller is an administrator.
     *
     * @throws ForbiddenOperationException when the caller is not an administrator
     */
    default void assertAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenOperationException("This operation requires administrator privileges.");
        }
    }
}
