package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserProfileResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        String status,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserProfileResponse from(User user) {
        Set<String> roleNames = user.getRoles() == null ? Set.of() :
                user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet());

        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getStatus(),
                roleNames,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

