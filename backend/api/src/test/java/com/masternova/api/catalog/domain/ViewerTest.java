package com.masternova.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.kernel.money.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The in-memory half of the visibility rule — an authorization property, with no database. */
class ViewerTest {

  static final UUID OWNER = UUID.randomUUID();
  static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  private static Course course(boolean published) {
    Course course =
        Course.draft(
            "k8s",
            "Kubernetes",
            "…",
            CourseLevel.BEGINNER,
            "en",
            Money.zero("INR"),
            new Category(),
            new Instructor(OWNER, "Asha"),
            NOW);
    if (published) {
      course.publish(NOW);
    }
    return course;
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
