package com.nhom6.motofix.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(
        @NotNull(message = "Điểm đánh giá rating không được để trống")
        @Min(value = 1, message = "Rating tối thiểu là 1")
        @Max(value = 5, message = "Rating tối đa là 5")
        Integer rating,

        String comment
) {
}

