package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.platform.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Duplicate this course" — the use case around the Prototype ({@link Course#duplicateAsDraft}).
 * The aggregate knows HOW to copy itself; this service decides WHO may, picks the new slug, and
 * makes the whole copy one transaction.
 *
 * <p>No {@code CourseDuplicator} interface: one implementation, forever (LLD §6).
 */
@Service
public class CourseDuplicationService {

  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

  private final CourseRepository courses;
  private final CourseAccess access;
  private final Clock clock;

  CourseDuplicationService(CourseRepository courses, CourseAccess access, Clock clock) {
    this.courses = courses;
    this.access = access;
    this.clock = clock;
  }

  /**
   * Copies a course and its curriculum into a new DRAFT owned by the same instructor.
   *
   * <p>Who may: the course's instructor, or an admin (on the instructor's behalf). Anyone else gets
   * the same 404 as for a missing course. Double submits are handled before this runs: the endpoint
   * requires an {@code Idempotency-Key}, so a retried click replays the first 201.
   *
   * @throws NotFoundException if the course doesn't exist or the actor may not duplicate it
   */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Course duplicate(UUID courseId, Viewer actor) {
    Course source = access.forAuthoring(courseId, actor);

    Course copy = source.duplicateAsDraft(copySlug(source.slug()), clock.instant());
    // ⭐ one save: cascade = ALL inserts the course, its sections and their lectures, all in this
    //    transaction — a half-copied course is never visible
    return courses.save(copy);
  }

  /**
   * {@code kubernetes-basics} → {@code kubernetes-basics-copy-x7k2q9}. Six random base-36
   * characters are 2 billion possibilities per course; the slug's UNIQUE constraint still guards
   * the rest.
   */
  private static String copySlug(String sourceSlug) {
    StringBuilder suffix = new StringBuilder("-copy-");
    for (int i = 0; i < 6; i++) {
      suffix.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
    }
    String base = sourceSlug.replaceAll("(-copy-[a-z0-9]{6})+$", ""); // no "-copy-…-copy-…"
    int room = 140 - suffix.length();
    return (base.length() > room ? base.substring(0, room).replaceAll("-+$", "") : base) + suffix;
  }
}
