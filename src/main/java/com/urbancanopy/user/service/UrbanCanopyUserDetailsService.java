package com.urbancanopy.user.service;

import com.urbancanopy.user.entity.User;
import com.urbancanopy.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads an account for authentication (FR-AUTH-05).
 *
 * <p>The failure message is identical whether the email is unknown or the account merely
 * cannot be found — and the login endpoint says the same thing when the password is wrong.
 * Distinguishing them would turn the login form into an oracle for which addresses hold
 * accounts here.</p>
 *
 * <p>A disabled or locked account is loaded rather than hidden: Spring Security then checks
 * the flags itself and raises {@code DisabledException} or {@code LockedException}, which the
 * failure handler turns into the specific, honest message those two cases deserve
 * (FR-AUTH-10, FR-AUTH-11). The distinction is safe to expose because the caller has already
 * proved the password.</p>
 */
@Service
public class UrbanCanopyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public UrbanCanopyUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(User.normaliseEmail(email))
                .map(UrbanCanopyUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email address or password."));
    }
}
