package com.urbancanopy.common.security;

import java.util.Optional;

/**
 * Port through which the security seam resolves an identity, without {@code common}
 * depending on the {@code user} feature package.
 *
 * <p>Dependency inversion: {@code common.security} declares what it needs; the
 * {@code user} package supplies it. Without this port, cross-cutting code would import a
 * feature package, and the architecture's dependency rule would be broken at its very
 * first opportunity.</p>
 */
public interface AuthenticatedUserLookup {

    /** Resolves an enabled account by its unique email address. */
    Optional<AuthenticatedUser> findByEmail(String email);

    /** Resolves an enabled account by its persistent identifier. */
    Optional<AuthenticatedUser> findById(Long id);
}
