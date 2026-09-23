package com.nhom6.motofix.dto.respond;

import java.util.Set;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        Set<String> roles,
        String status
) {
}
