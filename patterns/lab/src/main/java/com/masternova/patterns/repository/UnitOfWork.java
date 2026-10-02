package com.masternova.patterns.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <b>Unit of Work</b> — tracks everything loaded, added, changed and removed during ONE business
 * operation, and writes it all at commit — or nothing at all.
 *
 * <p>This is what Hibernate's persistence context + Spring's {@code @Transactional} do for you
 * (note 10 §5): an <b>identity map</b> (one object per row), <b>change tracking</b> (dirty
 * checking), and an atomic <b>commit</b>.
 */
public final class UnitOfWork {

  private final CourseRepository repository;
  private final Map<String, Course> identityMap = new HashMap<>(); // ⭐ one object per id
  private final Set<String> added = new LinkedHashSet<>();
  private final Set<String> dirty = new LinkedHashSet<>();
  private final Set<String> removed = new LinkedHashSet<>();
  private boolean finished;

  public UnitOfWork(CourseRepository repository) {
    this.repository = repository;
  }

  /** Loads through the identity map: the same id always returns the SAME object. */
  public Optional<Course> find(String id) {
    ensureOpen();
    if (identityMap.containsKey(id)) {
      return Optional.of(identityMap.get(id)); // ⭐ no second "query"
    }
    Optional<Course> loaded = repository.findById(id);
    loaded.ifPresent(c -> identityMap.put(id, c));
    return loaded;
  }

  public void registerNew(Course course) {
    ensureOpen();
    identityMap.put(course.id(), course);
    added.add(course.id());
  }

  /** Hibernate detects this automatically by comparing snapshots; here we register explicitly. */
  public void registerDirty(Course course) {
    ensureOpen();
    if (!added.contains(course.id())) {
      dirty.add(course.id());
    }
  }

  public void registerRemoved(Course course) {
    ensureOpen();
    if (added.remove(course.id())) {
      identityMap.remove(course.id()); // created and deleted in the same unit: never hits the DB
      return;
    }
    dirty.remove(course.id());
    removed.add(course.id());
  }

  /**
   * Validates everything first, THEN writes — so a rule violation leaves the database untouched
   * (all-or-nothing, the way a transaction rollback does).
   */
  public void commit() {
    ensureOpen();
    List<String> problems = new ArrayList<>();
    for (String id : added) {
      if (identityMap.get(id).title().isBlank()) {
        problems.add(id + ": title required");
      }
    }
    if (!problems.isEmpty()) {
      rollback();
      throw new IllegalStateException("commit rejected: " + problems);
    }
    added.forEach(id -> repository.add(identityMap.get(id)));
    dirty.forEach(id -> repository.update(identityMap.get(id)));
    removed.forEach(repository::remove);
    finished = true;
  }

  public void rollback() {
    identityMap.clear();
    added.clear();
    dirty.clear();
    removed.clear();
    finished = true;
  }

  private void ensureOpen() {
    if (finished) {
      throw new IllegalStateException("this unit of work is already finished");
    }
  }
}
