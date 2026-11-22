package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.SwitchRoleRequest;
import com.nhom6.motofix.dto.request.UpdateUserProfileRequest;
import com.nhom6.motofix.dto.respond.SwitchRoleResponse;
import com.nhom6.motofix.dto.respond.UserProfileResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * API 3: Xem thông tin cá nhân của người dùng đang đăng nhập.
     * GET /api/users/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(userService.getProfile(user));
    }

    /**
     * API 4: Cập nhật thông tin cá nhân (họ tên, số điện thoại, avatar, giới tính).
     * PUT /api/users/me
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUserProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateProfile(user, request));
    }

    /**
     * Chuyển đổi vai trò hiện tại (CUSTOMER <-> PROVIDER)
     * PATCH /api/users/me/current-role
     */
    @PatchMapping("/me/current-role")
    public ResponseEntity<SwitchRoleResponse> switchRole(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SwitchRoleRequest request
    ) {
        return ResponseEntity.ok(userService.switchRole(user, request));
    }
}
