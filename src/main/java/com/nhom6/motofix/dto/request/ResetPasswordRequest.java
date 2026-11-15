package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @JsonAlias({"current_password", "currentPassword"})
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @JsonAlias({"new_password", "newPassword"})
        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 6, max = 100, message = "Mật khẩu mới phải có từ 6 đến 100 ký tự")
        String newPassword,

        @JsonAlias({"confirm_password", "confirmPassword", "confirm_new_password"})
        @NotBlank(message = "Xác nhận mật khẩu mới không được để trống")
        String confirmPassword
) {
}

