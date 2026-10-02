package com.masternova.java.exceptions;

import com.masternova.java.streams.Course;
import com.masternova.java.valueobject.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link Optional} used the way it was designed: as a RETURN type that says "there may be no
 * answer", consumed with map / filter / flatMap / or / orElse… — never with a bare get().
 */
public final class CourseLookup {

  private final Map<String, Course> byId;
  private final Map<String, Course> bySlug;

  public CourseLookup(List<Course> courses) {
    this.byId = courses.stream().collect(Collectors.toMap(Course::id, Function.identity()));
    this.bySlug = courses.stream().collect(Collectors.toMap(CourseLookup::slug, Function.identity()));
  }

  /** ⭐ The absent case is in the TYPE — a caller can't forget it the way it can forget null. */
  public Optional<Course> findById(String id) {
    return Optional.ofNullable(byId.get(id)); // ofNullable: null → empty. of(null) would throw.
  }

  public Optional<Course> findBySlug(String slug) {
    return Optional.ofNullable(bySlug.get(slug));
  }

  /** The API accepts either an id or a slug: try one, then the other. */
  public Optional<Course> findByIdOrSlug(String key) {
    // ⭐ or(supplier) (Java 9): another Optional, computed ONLY if the first is empty.
    return findById(key).or(() -> findBySlug(key));
  }

  /** Throws the domain exception that becomes a 404 (see ProblemMapper). */
  public Course getOrThrow(String id) {
    // ⭐ orElseThrow(supplier): the exception is created only when needed.
    return findById(id).orElseThrow(() -> new NotFoundException("Course", id));
  }

  public String titleOrPlaceholder(String id) {
    return findById(id).map(Course::title).orElse("(unknown course)");
  }

  /** Price, but only for courses learners can see. */
  public Optional<Money> priceIfPublished(String id) {
    return findById(id).filter(Course::published).map(Course::price);
  }

  /** publishedOn is null for drafts — flatMap avoids an Optional<Optional<LocalDate>>. */
  public Optional<LocalDate> publishedOn(String id) {
    // map(c -> Optional.ofNullable(...)) would give Optional<Optional<LocalDate>>.
    return findById(id).flatMap(course -> Optional.ofNullable(course.publishedOn()));
  }

  static String slug(Course course) {
    return course.title().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}
