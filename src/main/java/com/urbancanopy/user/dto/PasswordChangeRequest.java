package com.urbancanopy.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Changes the caller's own password (FR-AUTH-09).
 *
 * <p>The current password is required even though the caller is already signed in. A session
 * left open on a shared machine is exactly the situation this guards: without it, anyone who
 * finds that session can lock the real owner out of their account permanently.</p>
 */
public record PasswordChangeRequest(

        @NotBlank(message = "Enter your current password.")
        String currentPassword,

        @NotBlank(message = "Enter a new password.")
        String newPassword
) {
}
