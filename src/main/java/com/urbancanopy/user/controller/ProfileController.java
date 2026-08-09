package com.urbancanopy.user.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.user.dto.ProfileUpdateRequest;
import com.urbancanopy.user.dto.UserResponse;
import com.urbancanopy.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own profile (FR-AUTH-09).
 *
 * <p>Addressed as {@code /profile} with no id in the path. An id would imply that some other value
 * could be supplied, and the endpoint would then need to prove it refuses one; without it, editing
 * another user's profile is not a request this API can express.</p>
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> findOwn() {
        return ResponseEntity.ok(ApiResponse.ok(userService.currentUser()));
    }

    /** Updates name, organisation and designation. Password change arrives in Phase 12. */
    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateOwn(
            @Valid @RequestBody ProfileUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateOwnProfile(request), "Your profile has been updated."));
    }
}
