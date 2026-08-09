package com.urbancanopy.user.service;

import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.common.exception.ForbiddenOperationException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.plan.repository.PlantationPlanRepository;
import com.urbancanopy.user.dto.ProfileUpdateRequest;
import com.urbancanopy.user.dto.UserResponse;
import com.urbancanopy.user.dto.UserSummary;
import com.urbancanopy.user.entity.Role;
import com.urbancanopy.user.entity.User;
import com.urbancanopy.user.mapper.UserMapper;
import com.urbancanopy.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Account queries, self-service profile edits, and user administration (FR-AUTH-09,
 * FR-ADM-01 … FR-ADM-03).
 *
 * <h2>What is not here</h2>
 * <p>Registration, login, logout and password change are absent, and their absence is scheduled
 * rather than overlooked. Each of them needs a password encoder, and
 * {@code spring-boot-starter-security} is deliberately kept out of the build until Phase 12 so
 * that no throwaway permissive configuration is written now and deleted later. What Phase 12 adds
 * is those four operations; what it does not have to change is anything in this class, because
 * identity already arrives through {@link CurrentUserProvider}.</p>
 *
 * <h2>The two guards that matter</h2>
 * <p>An administrator cannot disable or demote their own account (FR-ADM-03), and the system will
 * not permit the last enabled administrator to be removed by either route (BR-22). Between them
 * these prevent the one failure that cannot be repaired through the interface: an installation
 * with no one able to administer it.</p>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String RESOURCE = "User";

    private final UserRepository userRepository;
    private final PlantationPlanRepository planRepository;
    private final UserMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PlantationPlanRepository planRepository,
                       UserMapper mapper,
                       CurrentUserProvider currentUserProvider,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    // -------------------------------------------------------------------- self service

    /** The caller's own account — the payload behind {@code GET /auth/me}. */
    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        return mapper.toResponse(requireCallerEntity());
    }

    /** Updates the caller's own profile (FR-AUTH-09). */
    @Transactional
    public UserResponse updateOwnProfile(ProfileUpdateRequest request) {
        User user = requireCallerEntity();

        user.setFullName(request.fullName().trim());
        user.setOrganisation(trimToNull(request.organisation()));
        user.setDesignation(trimToNull(request.designation()));

        User saved = userRepository.save(user);
        log.info("Profile updated for user {}", saved.getId());

        return mapper.toResponse(saved);
    }

    // ----------------------------------------------------------------- administration

    /** All accounts, searchable and filterable (FR-ADM-01). */
    @Transactional(readOnly = true)
    public PageResponse<UserSummary> search(String search, Role role, Boolean enabled,
                                            Pageable pageable) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        currentUserProvider.assertAdmin();

        Page<User> page = userRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, role, enabled, pageable);

        Map<Long, Long> planCounts = planCounts(page.getContent());

        return PageResponse.from(page.map(user -> mapper.toSummary(
                user,
                planCounts.getOrDefault(user.getId(), 0L),
                caller.owns(user.getId()))));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        currentUserProvider.assertAdmin();
        return mapper.toResponse(requireById(id));
    }

    /**
     * Enables or disables an account (FR-ADM-02, FR-AUTH-10).
     *
     * <p>Takes effect on the next request without any session bookkeeping, because
     * {@code UserLookupAdapter} resolves only enabled accounts — a disabled user simply stops
     * being resolvable as a caller.</p>
     */
    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        currentUserProvider.assertAdmin();

        User user = requireById(id);

        if (caller.owns(id) && !enabled) {
            throw new ForbiddenOperationException(
                    "You cannot disable your own account. Ask another administrator to do it.");
        }

        if (!enabled && user.isAdmin()) {
            assertNotTheLastAdministrator(user,
                    "Disabling this account would leave the system with no enabled administrator.");
        }

        if (user.isEnabled() == enabled) {
            return mapper.toResponse(user);
        }

        user.setEnabled(enabled);
        User saved = userRepository.save(user);

        auditService.record(
                enabled ? AuditEventType.ACCOUNT_ENABLED : AuditEventType.ACCOUNT_DISABLED,
                RESOURCE, id,
                "%s account '%s'".formatted(enabled ? "Enabled" : "Disabled", saved.getEmail()));

        log.info("Account {} {} by administrator {}", id, enabled ? "enabled" : "disabled", caller.id());
        return mapper.toResponse(saved);
    }

    /**
     * Changes an account's role (FR-ADM-02, FR-AUTH-07).
     *
     * <p>A demotion is checked against BR-22 for the same reason a disable is: withdrawing the
     * last administrator's rights and disabling the last administrator's account leave the
     * installation in exactly the same unrecoverable state.</p>
     */
    @Transactional
    public UserResponse changeRole(Long id, Role role) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        currentUserProvider.assertAdmin();

        User user = requireById(id);
        Role previousRole = user.getRole();

        if (previousRole == role) {
            return mapper.toResponse(user);
        }

        if (caller.owns(id)) {
            throw new ForbiddenOperationException(
                    "You cannot change your own role. Ask another administrator to do it.");
        }

        if (previousRole.isAdmin() && !role.isAdmin()) {
            assertNotTheLastAdministrator(user,
                    "Withdrawing administrator rights from this account would leave the system with "
                            + "no enabled administrator.");
        }

        user.setRole(role);
        User saved = userRepository.save(user);

        auditService.recordChange(AuditEventType.ROLE_CHANGED, RESOURCE, id,
                "role", previousRole, role);

        log.info("Role of account {} changed from {} to {} by administrator {}",
                id, previousRole, role, caller.id());
        return mapper.toResponse(saved);
    }

    /** Role options for the administrator's role selector. */
    public List<EnumOption> roleOptions() {
        return EnumOption.from(Role.class, Role::getLabel);
    }

    // ---------------------------------------------------------------------- internals

    private User requireCallerEntity() {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        return userRepository.findById(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, caller.id()));
    }

    private User requireById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    /**
     * Refuses a change that would remove the final enabled administrator (BR-22).
     *
     * <p>The count is taken over enabled administrators, and the subject is only counted while it
     * is still one — so this correctly permits demoting an administrator when a second one exists,
     * and correctly refuses when they are the only one left.</p>
     */
    private void assertNotTheLastAdministrator(User subject, String consequence) {
        long enabledAdministrators = userRepository.countByRoleAndEnabledTrue(Role.ROLE_ADMIN);
        boolean subjectCounts = subject.isAdmin() && subject.isEnabled();

        if (subjectCounts && enabledAdministrators <= 1) {
            throw new BusinessRuleViolationException("BR-22",
                    consequence + " Promote another account to administrator first.");
        }
    }

    /** Live plan counts for a page of users, in one grouped query. */
    private Map<Long, Long> planCounts(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : planRepository.countByOwnerIds(userIds)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
