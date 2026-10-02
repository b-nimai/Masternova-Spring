package com.masternova.api.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password 8–72 characters. ⭐ 72 is not arbitrary: bcrypt only uses the first 72 BYTES of
 *     its input, so a longer password would be silently truncated.
 */
public record SignupRequest(
    @NotBlank @Size(max = 320) String email,
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank @Size(min = 8, max = 72) String password) {}
