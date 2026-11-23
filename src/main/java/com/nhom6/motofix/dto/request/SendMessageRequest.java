package com.nhom6.motofix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 1000, message = "Nội dung tin nhắn không được vượt quá 1000 ký tự")
        String message
) {}
