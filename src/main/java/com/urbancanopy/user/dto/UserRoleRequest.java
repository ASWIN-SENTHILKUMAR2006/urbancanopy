package com.urbancanopy.user.dto;

import com.urbancanopy.user.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Grants or withdraws administrator rights (FR-ADM-02, FR-AUTH-07).
 *
 * <p>Only an existing administrator may call the endpoint that accepts this, which is what makes
 * {@code ROLE_ADMIN} ungrantable by self-service.</p>
 */
public record UserRoleRequest(

        @NotNull(message = "A role is required.")
        Role role
) {
}
