package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.UpdateUserProfileRequest;
import com.nhom6.motofix.dto.respond.UserProfileResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    /**
     * API 3: Xem thông tin cá nhân của người dùng đang đăng nhập.
     * GET /api/users/me
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserProfileResponse.from(user);
    }

    /**
     * API 4: Cập nhật thông tin cá nhân (họ tên, số điện thoại, avatar).
     * PUT /api/users/me
     * Điều kiện: Người dùng chỉ được sửa thông tin của chính mình; kiểm tra số điện thoại không trùng người khác.
     */
    @Transactional
    public UserProfileResponse updateProfile(User currentUser, UpdateUserProfileRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        // Cập nhật họ tên nếu có
        if (request.fullName() != null) {
            String trimmedName = request.fullName().trim();
            if (trimmedName.isEmpty()) {
                throw new BadRequestException("Full name cannot be empty");
            }
            user.setFullName(trimmedName);
        }

        // Cập nhật số điện thoại nếu có và kiểm tra trùng lặp
        if (request.phone() != null) {
            String trimmedPhone = request.phone().trim();
            if (trimmedPhone.isEmpty()) {
                throw new BadRequestException("Phone number cannot be empty");
            }
            if (!trimmedPhone.equals(user.getPhone())) {
                userRepository.findByPhone(trimmedPhone).ifPresent(otherUser -> {
                    if (!otherUser.getId().equals(user.getId())) {
                        throw new BadRequestException("Phone already exists");
                    }
                });
                user.setPhone(trimmedPhone);
            }
        }

        // Cập nhật URL ảnh đại diện nếu có
        if (request.avatarUrl() != null) {
            String trimmedAvatar = request.avatarUrl().trim();
            user.setAvatarUrl(trimmedAvatar.isEmpty() ? null : trimmedAvatar);
        }

        User savedUser = userRepository.save(user);
        log.info("User {} updated profile successfully", savedUser.getId());

        return UserProfileResponse.from(savedUser);
    }
}

