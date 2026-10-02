package com.masternova.api.identity.web.dto;

/**
 * The access token for the client to keep IN MEMORY (ADR-0006). The refresh token is NOT here — it
 * travels only in an httpOnly cookie that JavaScript can't read.
 */
public record TokenResponse(
    String accessToken, String tokenType, long expiresIn, UserResponse user) {}
