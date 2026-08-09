package com.urbancanopy.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.user.dto.UserResponse;
import com.urbancanopy.user.entity.User;
import com.urbancanopy.user.mapper.UserMapper;
import com.urbancanopy.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;

/**
 * Tracks sign-in outcomes: resets the counters on success, counts failures, and locks an
 * account that has been guessed at too often (FR-AUTH-11).
 *
 * <h2>Why failures are counted from an event</h2>
 * <p>The failure handler is told an authentication failed, but not reliably which account it
 * was for — and a handler that re-reads the submitted username would count attempts against
 * addresses that do not exist. Spring publishes
 * {@link AuthenticationFailureBadCredentialsEvent} with the attempted principal, so the count
 * is applied to the real account or to nothing at all.</p>
 *
 * <p>An unknown email is deliberately not recorded anywhere that a caller can observe. Locking
 * or logging per unknown address would let someone map which addresses have accounts by timing
 * or by an administrator's audit log.</p>
 */
@Component
public class LoginAttemptHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptHandler.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;

    public LoginAttemptHandler(UserRepository userRepository,
                               UserMapper userMapper,
                               AuditService auditService,
                               ApplicationProperties properties,
                               ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * A successful sign-in: clear the failure counters, stamp the last-login time, and answer
     * with the account so the client need not immediately ask who it is.
     */
    @Transactional
    public void onSuccess(HttpServletRequest request, HttpServletResponse response,
                          Authentication authentication) throws IOException {

        issueFreshCsrfToken(request);

        UserResponse body = null;

        if (authentication.getPrincipal() instanceof UrbanCanopyUserDetails details) {
            User user = userRepository.findById(details.getId()).orElse(null);
            if (user != null) {
                user.recordSuccessfulLogin();
                userRepository.save(user);
                body = userMapper.toResponse(user);
            }

            auditService.record(AuditEventType.LOGIN_SUCCEEDED, "User", details.getId(),
                    "Signed in as " + details.getUsername());
            log.info("Sign-in succeeded for user {}", details.getId());
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.ok(body, "Signed in."));
    }

    /**
     * Materialises the post-login CSRF token so the sign-in response actually carries it.
     *
     * <p>Spring Security rotates the CSRF token on authentication — the old one is deleted so
     * that a token an attacker planted before sign-in cannot be reused after it. The
     * replacement, though, is <em>deferred</em>: it is only generated, and its cookie only
     * written, when something asks for it. Nothing does during the login request, so without
     * this call the client is left holding an empty token and its very first write after
     * signing in is rejected with 403.</p>
     *
     * <p>Reading the value is the whole point; it is discarded because the effect — the
     * {@code Set-Cookie} on this response — is what matters.</p>
     */
    private void issueFreshCsrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
    }

    /**
     * A failed sign-in against a real account: count it, and lock the account once the
     * configured threshold is reached.
     *
     * <p>Runs in its own transaction because the authentication that triggered it has already
     * failed — there is no business transaction left to join, and the count must survive
     * regardless.</p>
     */
    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = String.valueOf(event.getAuthentication().getName());

        userRepository.findByEmail(User.normaliseEmail(email)).ifPresent(user -> {
            ApplicationProperties.Security security = properties.security();

            user.recordFailedLogin(security.maxFailedAttempts(), Duration.ofMinutes(security.lockMinutes()));
            userRepository.save(user);

            auditService.record(AuditEventType.LOGIN_FAILED, "User", user.getId(),
                    "Failed sign-in attempt %d of %d".formatted(
                            user.getFailedLoginAttempts(), security.maxFailedAttempts()));

            if (user.isTemporarilyLocked()) {
                log.warn("Account {} locked until {} after {} failed attempts",
                        user.getId(), user.getLockExpiresAt(), user.getFailedLoginAttempts());
            }
        });
        // An unknown address falls through silently and on purpose — see the class comment.
    }
}
