package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateQuoteRequest(
        @NotBlank(message = "diagnosis_note is required")
        @JsonProperty("diagnosis_note")
        @JsonAlias({"diagnosisNote", "diagnosis_note"})
        String diagnosisNote,

        @NotEmpty(message = "items cannot be empty")
        @Valid
        List<CreateQuoteItemRequest> items
) {
}

