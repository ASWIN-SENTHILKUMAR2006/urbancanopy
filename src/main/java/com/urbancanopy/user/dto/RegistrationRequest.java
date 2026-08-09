package com.urbancanopy.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A self-service account registration (FR-AUTH-01).
 *
 * <p>There is no role field. Every self-registered account is a planner; {@code ROLE_ADMIN} is
 * grantable only by an existing administrator, through a different endpoint (FR-AUTH-07). A
 * role accepted here — however carefully validated — would be a privilege-escalation path
 * reachable by anyone with a browser.</p>
 *
 * <p>The password's own rules are checked in the service against the configured policy rather
 * than by an annotation here, because the policy is operator-tunable
 * ({@code app.password-policy.*}) and an annotation would freeze it at compile time.</p>
 */
public record RegistrationRequest(

        @NotBlank(message = "Your name is required.")
        @Size(max = 120, message = "Name must be at most 120 characters.")
        String fullName,

        @NotBlank(message = "An email address is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 160, message = "Email must be at most 160 characters.")
        String email,

        @NotBlank(message = "A password is required.")
        String password,

        @Size(max = 150, message = "Organisation must be at most 150 characters.")
        String organisation,

        @Size(max = 100, message = "Designation must be at most 100 characters.")
        String designation
) {
}
