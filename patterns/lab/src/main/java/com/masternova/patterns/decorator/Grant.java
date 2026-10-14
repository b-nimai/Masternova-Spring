package com.masternova.patterns.decorator;

import java.time.Instant;
import java.util.Optional;

/** The row being cached: who may watch which course, until when. */
public record Grant(String userId, String courseId, Optional<Instant> expiresAt) {}
