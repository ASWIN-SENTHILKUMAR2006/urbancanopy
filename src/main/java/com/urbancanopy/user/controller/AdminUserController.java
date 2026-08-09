package com.urbancanopy.user.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.user.dto.UserResponse;
import com.urbancanopy.user.dto.UserRoleRequest;
import com.urbancanopy.user.dto.UserStatusRequest;
import com.urbancanopy.user.dto.UserSummary;
import com.urbancanopy.user.entity.Role;
import com.urbancanopy.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User administration (FR-ADM-01 … FR-ADM-03).
 *
 * <p>There is no {@code DELETE}. Accounts own plans and appear as the actor on audit entries;
 * destroying one would either orphan that history or cascade it away, and an audit trail that can
 * be erased by removing its subject is not evidence. Disabling is the withdrawal this system
 * offers, and it takes effect on the next request.</p>
 *
 * <p>The refusals in FR-ADM-03 and BR-22 are enforced in the service, not here, because they are
 * domain rules rather than HTTP concerns — and because they must hold for any future caller, not
 * only for this controller.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /** All accounts, searchable by name, email or organisation (FR-ADM-01). */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserSummary>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
                userService.search(search, role, enabled, pageable)));
    }

    /** Role options for the role selector. */
    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<EnumOption>>> roles() {
        return ResponseEntity.ok(ApiResponse.ok(userService.roleOptions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id)));
    }

    /** Enables or disables an account (FR-ADM-02). */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> setStatus(
            @PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {

        UserResponse updated = userService.setEnabled(id, request.isEnabled());

        String message = request.isEnabled()
                ? "%s can sign in again.".formatted(updated.fullName())
                : "%s has been disabled and will be signed out on their next request."
                        .formatted(updated.fullName());

        return ResponseEntity.ok(ApiResponse.ok(updated, message));
    }

    /** Grants or withdraws administrator rights (FR-ADM-02, FR-AUTH-07). */
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            @PathVariable Long id, @Valid @RequestBody UserRoleRequest request) {

        UserResponse updated = userService.changeRole(id, request.role());
        return ResponseEntity.ok(ApiResponse.ok(updated,
                "%s is now a %s.".formatted(updated.fullName(), updated.role().label().toLowerCase())));
    }
}
