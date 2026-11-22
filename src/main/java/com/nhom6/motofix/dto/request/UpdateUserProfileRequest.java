package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserProfileRequest(
        @JsonAlias({"full_name", "fullName", "name"})
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @JsonAlias({"phone"})
        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone,

        @JsonAlias({"avatar_url", "avatarUrl", "avatar"})
        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl,

        @JsonAlias({"gender"})
        String gender
) {
    public UpdateUserProfileRequest(String fullName, String phone, String avatarUrl) {
        this(fullName, phone, avatarUrl, null);
    }
}
