package com.nhom6.motofix.dto.respond;

import java.util.Set;
import java.util.UUID;

public record LoginResponse<UserInfo>(
        String accessToken,

        String tokenType,

        UserInfo user
) {
    public record UserInfo(
            UUID id,
            String fullName,
            String email,
            Set<String> roles,
            String currentRole,
            String gender
    ) {
        public UserInfo(UUID id, String fullName, String email, Set<String> roles) {
            this(id, fullName, email, roles, null, null);
        }
    }
}
