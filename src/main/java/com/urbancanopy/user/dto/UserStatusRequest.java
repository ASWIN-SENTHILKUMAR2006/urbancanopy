package com.urbancanopy.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Enables or disables an account (FR-ADM-02, FR-AUTH-10).
 *
 * <p>Disabling rather than deleting is the only account removal this system offers. A user's
 * plans reference their account, and their audit entries record what they did; destroying the row
 * would either orphan that history or cascade it away, and neither is an acceptable answer to
 * "who made this change?".</p>
 */
public record UserStatusRequest(

        @NotNull(message = "Specify whether the account should be enabled.")
        Boolean enabled
) {

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
