package com.masternova.patterns.facade;

import com.masternova.patterns.facade.Subsystems.Catalog;
import com.masternova.patterns.facade.Subsystems.CourseFacts;
import com.masternova.patterns.facade.Subsystems.Purchases;
import com.masternova.patterns.facade.Subsystems.Rules;

/**
 * Facade: ONE method over three subsystems. It owns the SEQUENCE (facts → the row → the rules),
 * never the rules themselves — that's what keeps a facade from becoming a god object. (The real one
 * is {@code EntitlementService}.)
 */
public final class AccessFacade {

  private final Catalog catalog;
  private final Purchases purchases;
  private final Rules rules;

  public AccessFacade(Catalog catalog, Purchases purchases, Rules rules) {
    this.catalog = catalog;
    this.purchases = purchases;
    this.rules = rules;
  }

  public boolean mayWatch(String userId, String lectureId) {
    CourseFacts course =
        catalog
            .courseOfLecture(lectureId)
            .orElseThrow(() -> new IllegalArgumentException("no lecture " + lectureId));
    boolean owns = purchases.owns(userId, course.courseId()); // fetched ONCE, before the rules
    return rules.allows(userId, course, lectureId, owns);
  }
}
