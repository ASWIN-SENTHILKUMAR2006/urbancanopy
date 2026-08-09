package com.urbancanopy.user.mapper;

import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.user.dto.UserResponse;
import com.urbancanopy.user.dto.UserSummary;
import com.urbancanopy.user.entity.Role;
import com.urbancanopy.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * Translates {@link User} into its DTOs.
 *
 * <p>Neither output carries the password hash, the failed-attempt counter or the lock expiry.
 * The first is a credential; the other two are internal authentication state that no screen needs
 * and that would tell an attacker how close a guessing run is to a lockout.</p>
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getOrganisation(),
                user.getDesignation(),
                option(user.getRole()),
                user.isAdmin(),
                user.isEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    /**
     * A list row for the administrator's user table.
     *
     * @param planCount live plans this user owns, resolved for the whole page in one query
     * @param self      whether this row is the calling administrator's own account, so the
     *                  interface can disable the actions FR-ADM-03 refuses
     */
    public UserSummary toSummary(User user, long planCount, boolean self) {
        return new UserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getOrganisation(),
                user.getDesignation(),
                option(user.getRole()),
                user.isEnabled(),
                planCount,
                self,
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    public static EnumOption option(Role role) {
        return EnumOption.of(role.name(), role.getLabel());
    }
}
