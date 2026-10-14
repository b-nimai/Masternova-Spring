package com.masternova.patterns.decorator;

import java.util.Optional;

/** Component: what callers depend on. Every WRITER of the row is a method here. */
public interface GrantRepository {

  Optional<Grant> find(String userId, String courseId);

  void save(Grant grant);
}
