package com.urbancanopy.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changes a user makes to their own profile (FR-AUTH-09).
 *
 * <p>Email is not here. It is the login identifier and the unique key of the account; changing it
 * is an identity change rather than a profile edit, and doing it properly needs the verification
 * machinery that arrives with authentication in Phase 12. Accepting it now would mean either
 * building that verification early or letting an account's identity be reassigned by an
 * unverified request.</p>
 *
 * <p>Role and {@code enabled} are likewise absent, so this payload cannot be used to
 * self-promote. Both are administrator operations on a different endpoint (FR-AUTH-07).</p>
 */
public record ProfileUpdateRequest(

        @NotBlank(message = "Your name is required.")
        @Size(max = 120, message = "Name must be at most 120 characters.")
        String fullName,

        @Size(max = 150, message = "Organisation must be at most 150 characters.")
        String organisation,

        @Size(max = 100, message = "Designation must be at most 100 characters.")
        String designation
) {
}
