package com.masternova.api.enrollment.domain;

import java.util.UUID;

/** An enrollment's key: which learner, which course. */
public record UserCourse(UUID userId, UUID courseId) {}
