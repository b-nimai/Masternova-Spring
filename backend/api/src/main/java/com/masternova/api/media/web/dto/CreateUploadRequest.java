package com.masternova.api.media.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * "I'm about to upload this file." The size is what the plan is cut from and what complete checks
 * the provider against; the type is only a first filter (the worker's probe checks the content).
 */
public record CreateUploadRequest(
    @NotBlank @Size(max = 255) String filename,
    @NotBlank @Size(max = 100) String contentType,
    @NotNull @Positive Long sizeBytes) {}
