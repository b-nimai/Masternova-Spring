package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import java.util.List;

/**
 * One page of a keyset-paged list: the courses, and the opaque cursor for the next page ({@code
 * null} on the last page). No total — counting is the cost keyset pagination avoids (ADR-0009).
 */
public record CoursePage(List<Course> items, String nextCursor) {

  public CoursePage {
    items = List.copyOf(items);
  }

  static CoursePage empty() {
    return new CoursePage(List.of(), null);
  }
}
