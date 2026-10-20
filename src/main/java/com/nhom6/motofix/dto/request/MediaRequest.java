package com.nhom6.motofix.dto.request;

import com.nhom6.motofix.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MediaRequest(
        @NotBlank @Size(max = 2048)
        String url,

        @NotNull
        MediaType type
) {
}