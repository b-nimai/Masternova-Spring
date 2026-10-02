package com.masternova.java.sealed;

import java.util.Objects;

/**
 * What a lecture contains. A sealed CLASS (not interface) — useful when the subtypes share state
 * ({@code title}) and code.
 */
// ⭐ `permits` lists the ONLY allowed direct subclasses. They must be in the same package (or the
//    same module, when using Java modules), and each one must say how it continues the seal:
//      final       → no further subclasses           (VideoContent, ArticleContent)
//      sealed      → its own closed list             (not used here)
//      non-sealed  → re-opens: anyone may extend it  (QuizContent)
public abstract sealed class LectureContent permits VideoContent, ArticleContent, QuizContent {

  private final String title;

  // protected: only subclasses call it — `new LectureContent(...)` is impossible (abstract).
  protected LectureContent(String title) {
    this.title = Objects.requireNonNull(title, "title");
  }

  public String title() {
    return title;
  }
}
