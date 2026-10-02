package com.masternova.patterns.prototype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrototypeTest {

  static final UUID VIDEO = UUID.randomUUID();

  @Test
  void cloneIsShallowSoTheCopyAndTheOriginalShareSections() {
    ClonedCourse original = new ClonedCourse("Kubernetes");
    original.addSection(new Section("Intro"));

    ClonedCourse clone = original.clone();
    clone.sections().getFirst().rename("Renamed in the clone");
    clone.addSection(new Section("Added to the clone"));

    // ❌ both changes leaked into the original
    assertThat(original.sections().getFirst().title()).isEqualTo("Renamed in the clone");
    assertThat(original.sections()).hasSize(2);
    assertThat(clone.sections()).isSameAs(original.sections());
  }

  @Test
  void aCopyConstructorCopiesDeeplyAndResetsHistory() {
    Course original = new Course("Kubernetes");
    Section intro = new Section("Intro");
    intro.add(new Lecture("Welcome", 90, VIDEO));
    original.addSection(intro);
    original.publish();

    Course copy = original.copy("Kubernetes (copy)");
    copy.sections().getFirst().rename("Renamed in the copy");

    assertThat(original.sections().getFirst().title()).isEqualTo("Intro"); // ✅ untouched
    assertThat(copy.sections().getFirst()).isNotSameAs(original.sections().getFirst());
    assertThat(copy.isPublished()).isFalse(); // reset
    assertThat(original.isPublished()).isTrue();
  }

  @Test
  void immutablePartsAreSharedOnPurpose() {
    Course original = new Course("Kubernetes");
    Section intro = new Section("Intro");
    Lecture welcome = new Lecture("Welcome", 90, VIDEO);
    intro.add(welcome);
    original.addSection(intro);

    Lecture copied = original.copy("copy").sections().getFirst().lectures().getFirst();

    // ⭐ a record can't change, so the SAME instance is a correct copy — and the asset (gigabytes)
    //    is referenced, not duplicated
    assertThat(copied).isSameAs(welcome);
    assertThat(copied.assetId()).isEqualTo(VIDEO);
  }

  @Test
  void aPrototypeManagerHandsOutIndependentCopies() {
    Course bootcamp = new Course("Bootcamp");
    bootcamp.addSection(new Section("Week 1"));
    CourseTemplates templates = new CourseTemplates();
    templates.register("bootcamp", bootcamp);

    Course a = templates.newFrom("bootcamp", "Java bootcamp");
    Course b = templates.newFrom("bootcamp", "Go bootcamp");
    a.sections().getFirst().rename("Java week 1");

    assertThat(b.sections().getFirst().title()).isEqualTo("Week 1");
    assertThat(bootcamp.sections().getFirst().title()).isEqualTo("Week 1");
    assertThatThrownBy(() -> templates.newFrom("nope", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
