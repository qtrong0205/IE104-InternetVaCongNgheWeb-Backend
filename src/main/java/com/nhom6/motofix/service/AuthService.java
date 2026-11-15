package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.LoginRequest;
import com.nhom6.motofix.dto.request.RegisterRequest;
import com.nhom6.motofix.dto.request.ResetPasswordRequest;
import com.nhom6.motofix.dto.respond.LoginResponse;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.RegisterResponse;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.mapper.UserMapper;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // 1. Normalize dữ liệu
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();

        // 2. Kiểm tra email
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists");
        }

        // 3. Kiểm tra phone
        if (userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Phone already exists");
        }

        // 4. Tìm role USER
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() ->
                        new ResourceNotFoundException("USER role does not exist")
                );

        // 5. Tạo user
        User user = userMapper.toEntity(request);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(userRole);

        // 6. Lưu user
        User savedUser = userRepository.save(user);

        // 7. Convert roles
        var roles = savedUser.getRoles()
                .stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toSet());

        // 8. Response
        return userMapper.toResponse(savedUser);
    }

    /**
     * Xử lý đăng nhập và khởi tạo JWT Token
     */
    public LoginResponse login(LoginRequest request) {

        // 1. Chuẩn hóa email tìm kiếm
        String email = request.email().trim().toLowerCase();

        // 2. Tìm kiếm User theo email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid email or password")
                );

        // 3. Kiểm tra trạng thái tài khoản
        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        // 4. Kiểm tra tính hợp lệ của mật khẩu
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // 5. Tạo JWT Token
        String accessToken = jwtService.generateToken(user);

        // 6. Dùng UserMapper để ánh xạ sang UserInfo
        LoginResponse.UserInfo userInfo = userMapper.toLoginUserInfo(user);

        // 7. Trả về LoginResponse chứa Token và thông tin User
        return new LoginResponse(accessToken, "Bearer", userInfo);
    }

    /**
     * API 1: Đăng xuất người dùng
     * Xóa thông tin xác thực trong SecurityContext
     */
    public MessageResponse logout(User currentUser) {
        SecurityContextHolder.clearContext();
        return new MessageResponse("Logged out successfully");
    }

    /**
     * API 2: Đặt lại mật khẩu
     * Quy tắc nghiệp vụ:
     * - Người dùng phải đăng nhập
     * - Xác thực mật khẩu hiện tại
     * - Kiểm tra mật khẩu mới và mật khẩu xác nhận trùng khớp
     * - Mã hóa mật khẩu mới bằng PasswordEncoder (BCrypt)
     * - Không cho phép đặt mật khẩu mới trùng mật khẩu hiện tại
     */
    @Transactional
    public MessageResponse resetPassword(User currentUser, ResetPasswordRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        // 1. Xác thực mật khẩu hiện tại
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // 2. Kiểm tra mật khẩu mới và xác nhận mật khẩu mới có trùng khớp không
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation password do not match");
        }

        // 3. Không cho phép đặt mật khẩu mới trùng mật khẩu hiện tại
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must not be the same as the current password");
        }

        // 4. Mã hóa mật khẩu mới và lưu vào CSDL
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new MessageResponse("Password reset successfully");
    }
}
