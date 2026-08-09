package com.urbancanopy.config;

import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Wires JPA auditing so that {@code created_by} / {@code updated_by} are populated
 * automatically on every {@link com.urbancanopy.common.domain.AuditableEntity}.
 *
 * <p>The auditor is resolved through {@link CurrentUserProvider}, which means audit
 * attribution follows the security seam: it works today against the development identity
 * and will work unchanged against the authenticated principal from Phase 12.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** Fallback recorded when a write happens outside any request, e.g. data seeding. */
    private static final String SYSTEM_AUDITOR = "system";

    /**
     * Guards against the auditor lookup re-entering itself. See {@link #auditorAware}.
     *
     * <p>A {@code ThreadLocal} because a flush happens on the thread that triggered it, and the
     * recursion this breaks is entirely within one call stack.</p>
     */
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);

    /**
     * Supplies the current auditor's email.
     *
     * <p>{@link ObjectProvider} is used rather than direct injection because no
     * {@link CurrentUserProvider} bean exists when {@code app.dev.enabled} is false and
     * security has not yet been added — during that window every write is attributed to
     * {@value #SYSTEM_AUDITOR} instead of the context failing to start.</p>
     *
     * <h2>Why the re-entrancy guard exists</h2>
     * <p>This callback runs <strong>inside a flush</strong>. If resolving the auditor issues a
     * database query — which it does whenever the identity is not already known — that query
     * triggers an auto-flush, the auto-flush cascades to entities still pending insert, and each
     * of those fires this callback again. The result is unbounded recursion ending in a
     * {@code StackOverflowError}, and it surfaces only on the paths that write a <em>new child
     * entity</em>, which is why it can sit latent for a long time.</p>
     *
     * <p>The primary defence is that {@code CurrentUserProvider} resolves the caller once per
     * request and caches it, so no query happens on the flush path at all. This guard is the
     * second, independent one: if a future provider implementation queries anyway, the nested
     * call is answered with {@value #SYSTEM_AUDITOR} rather than recursing. Attribution of that
     * one nested write is imprecise; a stack overflow is fatal. The trade is not close.</p>
     */
    @Bean
    public AuditorAware<String> auditorAware(ObjectProvider<CurrentUserProvider> currentUserProvider) {
        return () -> {
            if (RESOLVING.get()) {
                return Optional.of(SYSTEM_AUDITOR);
            }

            RESOLVING.set(true);
            try {
                return Optional.ofNullable(currentUserProvider.getIfAvailable())
                        .flatMap(CurrentUserProvider::currentUser)
                        .map(AuthenticatedUser::email)
                        .or(() -> Optional.of(SYSTEM_AUDITOR));
            } finally {
                // remove() rather than set(false): the flag must not outlive the call on a
                // pooled request thread.
                RESOLVING.remove();
            }
        };
    }
}
