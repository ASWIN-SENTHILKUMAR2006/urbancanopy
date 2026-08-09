package com.urbancanopy.user.service;

import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.AuthenticatedUserLookup;
import com.urbancanopy.user.entity.User;
import com.urbancanopy.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Supplies the {@code common.security} package with identities, without that package
 * depending on this one.
 *
 * <p>The dependency points inward: {@code common.security} declares the
 * {@link AuthenticatedUserLookup} port, and this adapter — which lives with the data it
 * reads — implements it. Cross-cutting code therefore never imports a feature package.</p>
 *
 * <p>Only enabled accounts are resolvable. A disabled account is indistinguishable from a
 * non-existent one at this level, so disabling an account takes effect immediately across
 * every request without any caller needing to check.</p>
 */
@Service
public class UserLookupAdapter implements AuthenticatedUserLookup {

    private final UserRepository userRepository;

    public UserLookupAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findByEmail(String email) {
        return userRepository.findByEmail(User.normaliseEmail(email))
                .filter(User::isEnabled)
                .map(UserLookupAdapter::toAuthenticatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id)
                .filter(User::isEnabled)
                .map(UserLookupAdapter::toAuthenticatedUser);
    }

    static AuthenticatedUser toAuthenticatedUser(User user) {
        return new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getFullName(), user.isAdmin());
    }
}
