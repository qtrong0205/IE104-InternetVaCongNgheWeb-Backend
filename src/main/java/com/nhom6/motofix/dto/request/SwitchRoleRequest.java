package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SwitchRoleRequest(
        @JsonAlias({"targetRole", "role", "target_role"})
        @NotBlank(message = "Target role is required")
        String targetRole
) {}
