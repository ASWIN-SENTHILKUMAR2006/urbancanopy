package com.urbancanopy.user.service;

import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.user.entity.Role;
import com.urbancanopy.user.entity.User;
import com.urbancanopy.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives the seeded accounts a usable password on first start (FR-AUTH-08).
 *
 * <h2>Why the migration could not do this</h2>
 * <p>A password must be stored as a BCrypt hash, and SQL cannot compute one. The alternative —
 * committing a pre-computed hash to the migration — would put a known-plaintext administrator
 * password in version control, which is a backdoor regardless of how the deployment is
 * configured afterwards. The seed migration therefore writes a placeholder that is not a valid
 * BCrypt string, and this component replaces it with a real hash from external configuration.</p>
 *
 * <h2>Refusing is the safe default</h2>
 * <p>With no {@code app.security.seed-password} configured, nothing happens and the seeded
 * accounts remain unable to authenticate — the placeholder can never match any password, so a
 * misconfigured production deployment is locked, not open. The development profile supplies a
 * password so a fresh checkout is usable immediately; production does not.</p>
 *
 * <p>Accounts whose password has already been set are never touched, so this cannot reset a
 * real administrator's password on a later restart.</p>
 */
@Component
public class BootstrapCredentialsInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapCredentialsInitializer.class);

    /** The exact value {@code V2__seed_reference_data.sql} writes where a hash belongs. */
    static final String PLACEHOLDER_HASH = "NO-PASSWORD-SET-UNTIL-PHASE-12";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationProperties properties;

    public BootstrapCredentialsInitializer(UserRepository userRepository,
                                           PasswordEncoder passwordEncoder,
                                           ApplicationProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void openSeededAccounts() {
        ApplicationProperties.Security security = properties.security();

        List<User> pending = userRepository.findAll().stream()
                .filter(user -> PLACEHOLDER_HASH.equals(user.getPasswordHash()))
                .toList();

        if (pending.isEmpty()) {
            assertAnAdministratorCanSignIn();
            return;
        }

        if (!security.hasSeedPassword()) {
            log.warn("""

                    ============================================================================
                     {} seeded account(s) still have no password and cannot sign in.
                     Set app.security.seed-password (env UC_SEED_PASSWORD) and restart to open
                     them, then change the password immediately.
                    ============================================================================
                    """, pending.size());
            return;
        }

        String hash = passwordEncoder.encode(security.seedPassword());
        pending.forEach(user -> {
            user.setPasswordHash(hash);
            userRepository.save(user);
        });

        log.warn("""

                ============================================================================
                 SEEDED ACCOUNTS OPENED — {} account(s) were given the configured seed
                 password: {}
                 This password is known to anyone who can read the configuration.
                 Change it before this installation is used for anything real.
                ============================================================================
                """, pending.size(),
                pending.stream().map(User::getEmail).toList());
    }

    /**
     * Warns when no administrator can actually sign in.
     *
     * <p>An installation whose only administrator is disabled, or still unopened, cannot be
     * administered — and that is far easier to fix at startup than to diagnose later from a
     * screen that refuses every action.</p>
     */
    private void assertAnAdministratorCanSignIn() {
        boolean anyUsable = userRepository.findAll().stream()
                .anyMatch(user -> user.getRole() == Role.ROLE_ADMIN
                        && user.isEnabled()
                        && !PLACEHOLDER_HASH.equals(user.getPasswordHash()));

        if (!anyUsable) {
            log.warn("No enabled administrator with a usable password exists — the administration "
                    + "screens will be unreachable until one is created.");
        }
    }
}
