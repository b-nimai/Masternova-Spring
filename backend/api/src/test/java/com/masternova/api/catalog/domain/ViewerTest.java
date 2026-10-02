package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The in-memory half of the visibility rule — an authorization property, with no database. */
class ViewerTest {

  static final UUID OWNER = UUID.randomUUID();
  static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  private static Course course(boolean published) {
    CourseBuilder course = aCourse().by(new Instructor(OWNER, "Asha"));
    return (published ? course.published(NOW) : course).build();
  }

  @Test
  void anyoneSeesAPublishedCourse() {
    assertThat(Viewer.anonymous().canSee(course(true))).isTrue();
    assertThat(new Viewer.Member(UUID.randomUUID()).canSee(course(true))).isTrue();
  }

  @Test
  void aDraftIsVisibleOnlyToItsOwnerAndToAdmins() {
    Course draft = course(false);

    assertThat(Viewer.anonymous().canSee(draft)).isFalse();
    assertThat(new Viewer.Member(UUID.randomUUID()).canSee(draft)).isFalse(); // ⭐ another member
    assertThat(new Viewer.Member(OWNER).canSee(draft)).isTrue();
    assertThat(new Viewer.Admin(UUID.randomUUID()).canSee(draft)).isTrue();
  }
}
