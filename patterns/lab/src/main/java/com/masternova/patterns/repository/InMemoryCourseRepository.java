package com.masternova.patterns.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A second implementation of the same interface — the "seam" in action. Unit tests use this
 * instead of a database. (Production: a JPA/JDBC implementation.)
 */
public final class InMemoryCourseRepository implements CourseRepository {

  private final Map<String, Course> rows = new LinkedHashMap<>();
  private int writes;

  @Override
  public Optional<Course> findById(String id) {
    return Optional.ofNullable(rows.get(id)).map(Course::snapshot); // a fresh copy, like a DB read
  }

  @Override
  public List<Course> findPublished() {
    return rows.values().stream().filter(Course::published).map(Course::snapshot).toList();
  }

  @Override
  public void add(Course course) {
    if (rows.containsKey(course.id())) {
      throw new IllegalStateException("duplicate id " + course.id());
    }
    rows.put(course.id(), course.snapshot());
    writes++;
  }

  @Override
  public void update(Course course) {
    rows.put(course.id(), course.snapshot());
    writes++;
  }

  @Override
  public void remove(String id) {
    rows.remove(id);
    writes++;
  }

  /** How many write statements reached the "database" — lets tests see batching. */
  public int writes() {
    return writes;
  }
}
