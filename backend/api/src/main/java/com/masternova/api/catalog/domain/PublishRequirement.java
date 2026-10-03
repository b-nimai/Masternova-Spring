package com.masternova.api.catalog.domain;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * One named rule a course must meet before it can be submitted or published: a LEAF of the publish
 * gate's Specification. The {@code code} is the stable contract (the 422 and the wizard's checklist
 * use it); the message is copy.
 */
public record PublishRequirement(String code, String message, Predicate<Course> satisfiedBy) {

  public PublishRequirement {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(satisfiedBy, "satisfiedBy");
  }

  public boolean isSatisfiedBy(Course course) {
    return satisfiedBy.test(course);
  }
}
