package com.nhom6.motofix.dto.request;

import com.nhom6.motofix.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(
        @NotNull(message = "Phương thức thanh toán không được để trống (CASH hoặc BANK_TRANSFER)")
        PaymentMethod method
) {
}

