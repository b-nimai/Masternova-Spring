package com.masternova.patterns.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepositoryAndUnitOfWorkTest {

  @Test
  void theRepositoryHidesWhereDataLives() {
    CourseRepository courses = new InMemoryCourseRepository(); // a test double — no database
    Course spring = new Course("c1", "Spring Boot");
    spring.publish();
    courses.add(spring);
    courses.add(new Course("c2", "Draft"));

    assertThat(courses.findPublished()).extracting(Course::id).containsExactly("c1");
    assertThat(courses.findById("nope")).isEmpty();
  }

  @Test
  void readsReturnCopiesSoUnsavedChangesDontLeakIntoStorage() {
    CourseRepository courses = new InMemoryCourseRepository();
    courses.add(new Course("c1", "Spring Boot"));

    courses.findById("c1").orElseThrow().rename("changed but never saved");

    assertThat(courses.findById("c1").orElseThrow().title()).isEqualTo("Spring Boot");
  }

  @Test
  void theIdentityMapReturnsTheSameObjectForTheSameId() {
    InMemoryCourseRepository courses = new InMemoryCourseRepository();
    courses.add(new Course("c1", "Spring Boot"));
    UnitOfWork uow = new UnitOfWork(courses);

    assertThat(uow.find("c1").orElseThrow()).isSameAs(uow.find("c1").orElseThrow());
  }

  @Test
  void everythingIsWrittenAtCommitAndNotBefore() {
    InMemoryCourseRepository courses = new InMemoryCourseRepository();
    courses.add(new Course("c1", "Spring Boot"));
    int writesBefore = courses.writes();
    UnitOfWork uow = new UnitOfWork(courses);

    Course existing = uow.find("c1").orElseThrow();
    existing.rename("Spring Boot 4");
    uow.registerDirty(existing);
    uow.registerNew(new Course("c2", "Java Streams"));

    assertThat(courses.writes()).isEqualTo(writesBefore); // nothing written yet
    uow.commit();

    assertThat(courses.writes()).isEqualTo(writesBefore + 2);
    assertThat(courses.findById("c1").orElseThrow().title()).isEqualTo("Spring Boot 4");
    assertThat(courses.findById("c2")).isPresent();
  }

  @Test
  void aRejectedCommitWritesNothingAtAll() {
    InMemoryCourseRepository courses = new InMemoryCourseRepository();
    UnitOfWork uow = new UnitOfWork(courses);
    uow.registerNew(new Course("c1", "Valid"));
    uow.registerNew(new Course("c2", "  ")); // breaks a rule

    assertThatThrownBy(uow::commit).hasMessageContaining("c2: title required");

    assertThat(courses.findById("c1")).isEmpty(); // ⭐ all-or-nothing: the valid one wasn't written either
  }

  @Test
  void createdAndRemovedInOneUnitNeverReachesTheDatabase() {
    InMemoryCourseRepository courses = new InMemoryCourseRepository();
    UnitOfWork uow = new UnitOfWork(courses);
    Course temp = new Course("tmp", "Temporary");

    uow.registerNew(temp);
    uow.registerRemoved(temp);
    uow.commit();

    assertThat(courses.writes()).isZero();
  }
}
