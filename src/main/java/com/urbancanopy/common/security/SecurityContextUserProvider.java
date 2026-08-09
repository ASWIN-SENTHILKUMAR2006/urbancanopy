package com.urbancanopy.common.security;

import com.urbancanopy.user.service.UrbanCanopyUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The real answer to "who is asking?" (Phase 12).
 *
 * <p>This class is the entire cost of switching the application from a development identity to
 * real authentication. Every service asks {@link CurrentUserProvider}, so replacing the
 * implementation replaced the identity everywhere at once — no service, controller or ownership
 * rule changed. That was the point of the seam, declared in Phase 2 and paid off here.</p>
 *
 * <p>The anonymous authentication Spring installs for unauthenticated requests is filtered out
 * deliberately: {@code AnonymousAuthenticationToken} is technically an {@code Authentication},
 * and treating it as a caller would make {@code requireCurrentUser()} succeed for a visitor who
 * has not signed in.</p>
 */
@Component
public class SecurityContextUserProvider implements CurrentUserProvider {

    @Override
    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        // Only our own principal is accepted. Anything else — anonymous, or a principal from
        // some future authentication mechanism — is not an identified user of this application.
        return authentication.getPrincipal() instanceof UrbanCanopyUserDetails details
                ? Optional.of(details.toAuthenticatedUser())
                : Optional.empty();
    }
}
