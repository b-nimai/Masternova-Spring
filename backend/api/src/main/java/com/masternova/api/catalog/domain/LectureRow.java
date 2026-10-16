package com.masternova.api.catalog.domain;

import java.util.UUID;

/**
 * A JPQL constructor projection: one lecture with its course, read without loading the aggregate
 * ({@link CourseRepository#findLectureRowsByCourseIds}, {@link CourseRepository#findLectureRows}).
 */
public record LectureRow(
    UUID courseId, UUID lectureId, String title, LectureKind kind, LectureDuration duration) {}
