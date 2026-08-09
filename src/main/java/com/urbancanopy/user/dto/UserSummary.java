package com.urbancanopy.user.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.time.Instant;

/**
 * One row of the administrator's user list (FR-ADM-01).
 *
 * <p>{@code planCount} is present because it is the question an administrator is actually asking
 * when they look at this screen — whether an account is in use — and it is resolved for the whole
 * page in a single grouped query rather than one per row.</p>
 *
 * <p>{@code self} marks the administrator's own row, so the interface can disable the controls
 * that FR-ADM-03 forbids instead of offering an action that will be refused.</p>
 */
public record UserSummary(
        Long id,
        String fullName,
        String email,
        String organisation,
        String designation,
        EnumOption role,
        boolean enabled,
        long planCount,
        boolean self,
        Instant lastLoginAt,
        Instant createdAt
) {
}
