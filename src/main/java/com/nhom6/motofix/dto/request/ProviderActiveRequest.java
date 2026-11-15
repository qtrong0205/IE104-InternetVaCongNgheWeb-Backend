package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public record ProviderActiveRequest(
        @NotNull(message = "Trạng thái nhận đơn (isActive) không được để trống")
        @JsonAlias({"is_active", "isActive", "active", "is_online", "isOnline", "online"})
        Boolean isActive
) {
}

