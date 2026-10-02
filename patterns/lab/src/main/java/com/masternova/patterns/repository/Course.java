package com.masternova.patterns.repository;

import java.util.Objects;

/** A tiny entity: identity + mutable state (unlike a value object, note 01). */
public final class Course {

  private final String id;
  private String title;
  private boolean published;

  public Course(String id, String title) {
    this.id = Objects.requireNonNull(id, "id");
    this.title = Objects.requireNonNull(title, "title");
  }

  public String id() {
    return id;
  }

  public String title() {
    return title;
  }

  public boolean published() {
    return published;
  }

  public void rename(String newTitle) {
    this.title = Objects.requireNonNull(newTitle, "newTitle");
  }

  public void publish() {
    this.published = true;
  }

  /** A copy — what a "database row" holds, so changes to the live object aren't silently persisted. */
  Course snapshot() {
    Course copy = new Course(id, title);
    copy.published = published;
    return copy;
  }
}
