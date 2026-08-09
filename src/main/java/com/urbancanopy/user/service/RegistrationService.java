package com.urbancanopy.user.service;

import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.common.exception.DuplicateResourceException;
import com.urbancanopy.common.exception.ForbiddenOperationException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.user.dto.PasswordChangeRequest;
import com.urbancanopy.user.dto.RegistrationRequest;
import com.urbancanopy.user.dto.UserResponse;
import com.urbancanopy.user.entity.Role;
import com.urbancanopy.user.entity.User;
import com.urbancanopy.user.mapper.UserMapper;
import com.urbancanopy.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Account creation and password changes (FR-AUTH-01 … FR-AUTH-04, FR-AUTH-09).
 *
 * <p>Kept apart from {@code UserService}, which manages accounts that already exist. This
 * class is the only place in the application that touches a raw password, which makes the
 * answer to "where do passwords get handled?" a single file rather than a search.</p>
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private static final String RESOURCE = "User";

    private final UserRepository userRepository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ApplicationProperties properties;

    public RegistrationService(UserRepository userRepository,
                               UserMapper mapper,
                               PasswordEncoder passwordEncoder,
                               CurrentUserProvider currentUserProvider,
                               AuditService auditService,
                               ApplicationProperties properties) {
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Registers a planner (FR-AUTH-01, FR-AUTH-07).
     *
     * <p>Always {@link Role#ROLE_USER}. The request has no role field and this method does not
     * read one from anywhere.</p>
     */
    @Transactional
    public UserResponse register(RegistrationRequest request) {
        String email = User.normaliseEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            // The address is already known, and saying so is unavoidable: a registration form
            // cannot both refuse duplicates and conceal which addresses exist.
            throw DuplicateResourceException.of("email", "Email address", request.email());
        }

        assertPasswordMeetsPolicy(request.password());

        User user = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.ROLE_USER);
        user.setOrganisation(trimToNull(request.organisation()));
        user.setDesignation(trimToNull(request.designation()));

        User saved = userRepository.save(user);

        auditService.record(AuditEventType.ACCOUNT_REGISTERED, RESOURCE, saved.getId(),
                "Account registered for " + saved.getEmail());
        log.info("Account registered: {} (id={})", saved.getEmail(), saved.getId());

        return mapper.toResponse(saved);
    }

    /**
     * Changes the caller's own password (FR-AUTH-09).
     *
     * <p>The current password is verified first. Doing so costs a BCrypt comparison and buys
     * the guarantee that possession of a session is not by itself enough to take an account
     * over.</p>
     */
    @Transactional
    public void changeOwnPassword(PasswordChangeRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        User user = userRepository.findById(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, caller.id()));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ForbiddenOperationException("Your current password is not correct.");
        }

        assertPasswordMeetsPolicy(request.newPassword());

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleViolationException(
                    "Your new password must be different from your current one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        auditService.record(AuditEventType.PASSWORD_CHANGED, RESOURCE, user.getId(),
                "Password changed for " + user.getEmail());
        log.info("Password changed for user {}", user.getId());
    }

    /**
     * Enforces the configured password policy (FR-AUTH-03).
     *
     * <p>The message states every unmet rule at once. Revealing them one refusal at a time
     * turns choosing a password into a guessing game against the form.</p>
     */
    private void assertPasswordMeetsPolicy(String password) {
        ApplicationProperties.PasswordPolicy policy = properties.passwordPolicy();
        StringBuilder problems = new StringBuilder();

        if (password.length() < policy.minLength()) {
            problems.append("be at least ").append(policy.minLength()).append(" characters");
        }
        if (password.length() > policy.maxLength()) {
            append(problems, "be at most " + policy.maxLength() + " characters");
        }
        if (policy.requireLetter() && password.chars().noneMatch(Character::isLetter)) {
            append(problems, "contain a letter");
        }
        if (policy.requireDigit() && password.chars().noneMatch(Character::isDigit)) {
            append(problems, "contain a digit");
        }

        if (!problems.isEmpty()) {
            throw new BusinessRuleViolationException("FR-AUTH-03",
                    "Your password must " + problems + ".");
        }
    }

    private static void append(StringBuilder problems, String clause) {
        if (!problems.isEmpty()) {
            problems.append(", and ");
        }
        problems.append(clause);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
