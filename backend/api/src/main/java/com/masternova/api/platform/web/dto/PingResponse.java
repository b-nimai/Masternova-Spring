package com.masternova.api.platform.web.dto;

import java.time.Instant;

/**
 * Response body of {@code GET /api/v1/meta/ping}.
 *
 * <p>DTOs are records in the module's {@code web.dto} package: immutable, no behaviour, no JPA
 * annotations. Entities never cross the HTTP boundary.
 */
public record PingResponse(String status, String version, Instant time) {}
