package com.masternova.java.oop;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.oop.dispatch.DispatchTraps;
import com.masternova.java.oop.dispatch.DispatchTraps.Lecture;
import com.masternova.java.oop.dispatch.DispatchTraps.PaidCourse;
import com.masternova.java.oop.dispatch.DispatchTraps.VideoLecture;
import org.junit.jupiter.api.Test;

class DispatchTrapsTest {

  @Test
  void overridingIsDecidedAtRuntimeByTheActualObject() {
    Lecture lecture = new VideoLecture(); // declared Lecture, actually a VideoLecture

    assertThat(lecture.kind()).isEqualTo("video"); // ⭐ the object's class wins
  }

  @Test
  void overloadingIsDecidedAtCompileTimeByTheDeclaredType() {
    Lecture lecture = new VideoLecture();
    VideoLecture video = new VideoLecture();

    // ⭐ same object, different DECLARED types → different overloads. The runtime class is ignored.
    assertThat(DispatchTraps.describe(lecture)).isEqualTo("describe(Lecture)");
    assertThat(DispatchTraps.describe(video)).isEqualTo("describe(VideoLecture)");
  }

  @Test
  void fieldsAreNotPolymorphic() {
    Lecture lecture = new VideoLecture();
    VideoLecture video = (VideoLecture) lecture;

    assertThat(lecture.label).isEqualTo("lecture"); // ⚠️ the declared type's field
    assertThat(video.label).isEqualTo("video"); //      same object, other field
  }

  @Test
  void staticMethodsAreHiddenNotOverridden() {
    assertThat(Lecture.category()).isEqualTo("content");
    assertThat(VideoLecture.category()).isEqualTo("media");
  }

  @Test
  void clashingDefaultMethodsMustBeResolvedExplicitly() {
    assertThat(new PaidCourse().label()).isEqualTo("purchasable+shareable");
  }
}
