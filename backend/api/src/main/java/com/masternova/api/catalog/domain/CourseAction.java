package com.masternova.api.catalog.domain;

/** What someone may ask a course's lifecycle to do (the EVENTS of the state machine). */
public enum CourseAction {
  SUBMIT,
  WITHDRAW,
  PUBLISH,
  UNPUBLISH,
  ARCHIVE;

  /** ⭐ The two edges that lead toward the public catalog re-run the publish gate. */
  public boolean isGated() {
    return this == SUBMIT || this == PUBLISH;
  }
}
