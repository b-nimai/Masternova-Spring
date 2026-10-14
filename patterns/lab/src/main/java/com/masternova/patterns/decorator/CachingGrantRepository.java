package com.masternova.patterns.decorator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decorator: SAME interface, HOLDS another implementation, adds caching. (The real one is {@code
 * CachedEntitlementRepository}: Redis, eviction after commit, failing open.)
 *
 * <ul>
 *   <li>negatives are cached too ({@code Optional.empty()} is a value, not a miss);
 *   <li>a write DELETES the entry — never writes the new value through.
 * </ul>
 */
public final class CachingGrantRepository implements GrantRepository {

  private final GrantRepository inner;
  private final Map<String, Optional<Grant>> cache = new HashMap<>();

  public CachingGrantRepository(GrantRepository inner) {
    this.inner = inner;
  }

  @Override
  public Optional<Grant> find(String userId, String courseId) {
    return cache.computeIfAbsent(userId + ":" + courseId, k -> inner.find(userId, courseId));
  }

  @Override
  public void save(Grant grant) {
    inner.save(grant); // ⭐ always delegate first …
    cache.remove(grant.userId() + ":" + grant.courseId()); // … then add our behaviour
  }
}
