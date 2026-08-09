package com.urbancanopy.user.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.time.Instant;

/**
 * An account as its owner or an administrator sees it (FR-AUTH-09).
 *
 * <p>There is no password field, and there is no field from which a password could be derived.
 * The omission is the mechanism: {@code passwordHash} is not absent from this record because a
 * serialiser was configured to ignore it — a configuration that can be changed by accident —
 * but because the type has no such component at all (FR-AUTH-02, BR-02).</p>
 *
 * @param admin convenience flag beside {@code role}, so the interface can decide whether to
 *              render the admin navigation without knowing which role names are privileged
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String organisation,
        String designation,
        EnumOption role,
        boolean admin,
        boolean enabled,
        Instant lastLoginAt,
        Instant createdAt
) {
}
