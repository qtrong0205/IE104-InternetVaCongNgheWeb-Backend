package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.enums.MediaType;

public record UploadMediaResponse(String url, MediaType type) {}