package com.nhom6.motofix.dto.respond;

public record SwitchRoleResponse(
        String currentRole,
        String token,
        String message
) {
    public SwitchRoleResponse(String currentRole, String message) {
        this(currentRole, null, message);
    }
}
