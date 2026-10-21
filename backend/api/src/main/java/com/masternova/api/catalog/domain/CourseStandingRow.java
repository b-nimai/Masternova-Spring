package com.masternova.api.catalog.domain;

import java.util.UUID;

/** A JPQL constructor projection: a course's teacher and status, without the aggregate. */
public record CourseStandingRow(UUID courseId, UUID instructorId, CourseStatus status) {}
