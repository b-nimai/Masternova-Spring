package com.masternova.patterns.decorator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** ConcreteComponent: the "database". Doesn't know it's cached; counts reads for the test. */
public final class InMemoryGrantRepository implements GrantRepository {

  private final Map<String, Grant> rows = new HashMap<>();
  private int reads;

  @Override
  public Optional<Grant> find(String userId, String courseId) {
    reads++;
    return Optional.ofNullable(rows.get(userId + ":" + courseId));
  }

  @Override
  public void save(Grant grant) {
    rows.put(grant.userId() + ":" + grant.courseId(), grant);
  }

  public int reads() {
    return reads;
  }
}
