package com.urbancanopy.user.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.user.dto.PasswordChangeRequest;
import com.urbancanopy.user.dto.RegistrationRequest;
import com.urbancanopy.user.dto.UserResponse;
import com.urbancanopy.user.service.RegistrationService;
import com.urbancanopy.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, identity and password change.
 *
 * <p><strong>{@code /login} and {@code /logout} are deliberately absent from this class.</strong>
 * They are handled by Spring Security's own filters, configured in
 * {@link com.urbancanopy.config.SecurityConfig} — a controller method cannot establish a session,
 * rotate its id against fixation, or clear the security context, so writing one that appeared to
 * would be a convincing imitation of authentication rather than the thing itself.</p>
 *
 * <p>{@code /me} is what the browser shell calls on load, to decide whether to render the admin
 * navigation and whose name to show.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final RegistrationService registrationService;

    public AuthController(UserService userService, RegistrationService registrationService) {
        this.userService = userService;
        this.registrationService = registrationService;
    }

    /**
     * Creates a planner account (FR-AUTH-01).
     *
     * <p>Registering does not sign the new user in. Doing so would mean a single request both
     * creates an identity and assumes it, which is a longer step than it looks; sending them to
     * the sign-in page proves the credentials they just chose actually work.</p>
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegistrationRequest request) {

        UserResponse created = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created,
                "Your account has been created. Please sign in."));
    }

    /**
     * Changes the caller's own password (FR-AUTH-09).
     *
     * <p>Under {@code /auth} rather than {@code /profile}: a password is a credential, not a
     * profile field, and the two are edited by different forms with different rules.</p>
     */
    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody PasswordChangeRequest request) {

        registrationService.changeOwnPassword(request);
        return ResponseEntity.ok(ApiResponse.message("Your password has been changed."));
    }

    /**
     * The current account.
     *
     * <p>Returns 401 through the exception handler when no caller can be identified, rather than
     * an empty success — the client's "am I signed in?" question deserves a status code it can
     * branch on without inspecting the body.</p>
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(userService.currentUser()));
    }
}
