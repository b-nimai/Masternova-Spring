package com.masternova.java.sealed;

import com.masternova.java.valueobject.LectureDuration;
import java.util.Objects;

/** A video lecture. {@code final}: the hierarchy stops here. */
public final class VideoContent extends LectureContent {

  private final LectureDuration length;

  public VideoContent(String title, LectureDuration length) {
    super(title); // ⭐ a subclass constructor must call the parent constructor first
    this.length = Objects.requireNonNull(length, "length");
  }

  public LectureDuration length() {
    return length;
  }
}
