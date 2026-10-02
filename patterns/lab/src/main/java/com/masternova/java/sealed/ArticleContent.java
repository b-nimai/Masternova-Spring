package com.masternova.java.sealed;

/** A reading lecture. {@code final}: the hierarchy stops here. */
public final class ArticleContent extends LectureContent {

  private final int wordCount;

  public ArticleContent(String title, int wordCount) {
    super(title);
    if (wordCount < 0) {
      throw new IllegalArgumentException("wordCount cannot be negative: " + wordCount);
    }
    this.wordCount = wordCount;
  }

  public int wordCount() {
    return wordCount;
  }
}
