package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.ForgotPasswordRequest;
import com.nhom6.motofix.dto.request.LoginRequest;
import com.nhom6.motofix.dto.request.RegisterRequest;
import com.nhom6.motofix.dto.request.ResetPasswordRequest;
import com.nhom6.motofix.dto.request.ResetPasswordWithTokenRequest;
import com.nhom6.motofix.dto.respond.LoginResponse;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.RegisterResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * API 1: Đăng xuất.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(authService.logout(user));
    }

    /**
     * API: Quên mật khẩu qua email từ trang login.
     * POST /api/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    /**
     * API: Đặt lại mật khẩu bằng reset token nhận từ email.
     * POST /api/auth/reset-password-with-token
     */
    @PostMapping("/reset-password-with-token")
    public ResponseEntity<MessageResponse> resetPasswordWithToken(
            @Valid @RequestBody ResetPasswordWithTokenRequest request
    ) {
        return ResponseEntity.ok(authService.resetPasswordWithToken(request));
    }

    /**
     * API 2: Đổi mật khẩu trong phiên (đã đăng nhập).
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return ResponseEntity.ok(authService.resetPassword(user, request));
    }
}
