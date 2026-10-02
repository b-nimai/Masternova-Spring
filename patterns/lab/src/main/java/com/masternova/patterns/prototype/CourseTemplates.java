package com.masternova.patterns.prototype;

import java.util.HashMap;
import java.util.Map;

/**
 * GoF's PROTOTYPE MANAGER: a registry of ready-made prototypes. "New course from the bootcamp
 * template" = copy the registered prototype — no subclass per template, no factory that knows how to
 * build each one from scratch.
 */
public final class CourseTemplates {

  private final Map<String, Course> prototypes = new HashMap<>();

  public void register(String name, Course prototype) {
    prototypes.put(name, prototype);
  }

  public Course newFrom(String name, String title) {
    Course prototype = prototypes.get(name);
    if (prototype == null) {
      throw new IllegalArgumentException("no template " + name);
    }
    return prototype.copy(title); // ⭐ every caller gets an INDEPENDENT copy
  }
}
