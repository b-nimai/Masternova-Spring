package com.masternova.patterns.repository;

import java.util.List;
import java.util.Optional;

/**
 * <b>Repository</b> — a collection-like interface for one AGGREGATE, in domain language. The
 * service depends on this; where the data really lives (Postgres, memory, an API) is an
 * implementation detail behind it.
 */
public interface CourseRepository {

  Optional<Course> findById(String id);

  List<Course> findPublished();

  void add(Course course);

  void update(Course course);

  void remove(String id);
}
