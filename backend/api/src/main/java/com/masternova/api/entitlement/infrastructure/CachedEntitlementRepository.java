package com.masternova.api.entitlement.infrastructure;

import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementRepository;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache-aside in Redis in front of the JDBC repository (ADR-0016).
 *
 * <p>⭐ DECORATOR: same interface, wraps another implementation, adds caching. Force: a row read on
 * every decision and written about twice in its life — and the JDBC class must not know it's cached
 * (its IT tests SQL; this class's unit test tests invalidation). Removing the cache is removing one
 * wrapper in {@code EntitlementConfig}.
 *
 * <ul>
 *   <li>⭐ The ROW is cached, never the decision: the decision also depends on catalog's data.
 *   <li>⭐ Negatives too ({@value #NONE}): a stranger opening paid courses is the traffic to absorb.
 *   <li>⭐ Writes DELETE the key AFTER COMMIT, never write through: before the commit, a concurrent
 *       read would reload and re-cache the old row.
 *   <li>⭐ Fails open: a Redis error is counted, logged, and the database answers. A failed eviction
 *       never fails the write — the TTL is the backstop.
 * </ul>
 */
@DesignPattern(
    value = Pattern.DECORATOR,
    role = "Decorator: cache-aside, evict after commit, fail open",
    note = "patterns/docs/13-decorator.md")
public final class CachedEntitlementRepository implements EntitlementRepository {

  private static final Logger log = LoggerFactory.getLogger(CachedEntitlementRepository.class);

  /** The cached "no row" — distinct from a cache miss ({@code null}). */
  static final String NONE = "-";

  private final EntitlementRepository inner;
  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final Duration ttl;
  private final Counter hits;
  private final Counter misses;
  private final Counter errors;

  public CachedEntitlementRepository(
      EntitlementRepository inner,
      StringRedisTemplate redis,
      JsonMapper json,
      Duration ttl,
      MeterRegistry meters) {
    this.inner = inner;
    this.redis = redis;
    this.json = json;
    this.ttl = ttl;
    this.hits = counter(meters, "hit");
    this.misses = counter(meters, "miss");
    this.errors = counter(meters, "error");
  }

  /** ⭐ The version in the key: a changed record shape is a new key, not a parse error. */
  static String key(UUID userId, UUID courseId) {
    return "entitlement:v1:" + userId + ":" + courseId;
  }

  @Override
  public Optional<Entitlement> find(UUID userId, UUID courseId) {
    String key = key(userId, courseId);
    Hit cached = read(key);
    if (cached != null) {
      hits.increment();
      return cached.row();
    }
    misses.increment();
    Optional<Entitlement> row = inner.find(userId, courseId);
    write(key, row);
    return row;
  }

  @Override
  public boolean grant(Entitlement grant) {
    boolean changed = inner.grant(grant);
    evictAfterCommit(key(grant.userId(), grant.courseId()));
    return changed;
  }

  @Override
  public boolean revoke(UUID userId, UUID courseId, Entitlement.Revocation revocation) {
    boolean changed = inner.revoke(userId, courseId, revocation);
    evictAfterCommit(key(userId, courseId));
    return changed;
  }

  /** A cached answer — which may be "there is no row". */
  private record Hit(Optional<Entitlement> row) {}

  /** The cached answer, or {@code null} on a miss, a Redis error or an unreadable value. */
  private @Nullable Hit read(String key) {
    String value;
    try {
      value = redis.opsForValue().get(key);
    } catch (RuntimeException e) {
      failed("read", key, e);
      return null;
    }
    if (value == null) {
      return null;
    }
    if (NONE.equals(value)) {
      return new Hit(Optional.empty());
    }
    try {
      return new Hit(Optional.of(json.readValue(value, Entitlement.class)));
    } catch (JacksonException e) { // a corrupt value: drop it and ask the database
      failed("parse", key, e);
      delete(key);
      return null;
    }
  }

  private void write(String key, Optional<Entitlement> row) {
    try {
      redis.opsForValue().set(key, row.map(json::writeValueAsString).orElse(NONE), ttl);
    } catch (RuntimeException e) {
      failed("write", key, e);
    }
  }

  /**
   * ⭐ After the commit, so no reader can re-cache the old row between our delete and the commit. (A
   * reader that loaded the old row BEFORE the commit can still write it back after the delete: that
   * stale entry lives one TTL — the accepted race of ADR-0016.)
   */
  private void evictAfterCommit(String key) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      delete(key);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            delete(key);
          }
        });
  }

  private void delete(String key) {
    try {
      redis.delete(key);
    } catch (RuntimeException e) {
      failed("evict", key, e); // the write committed; never undo it over a cache
    }
  }

  private void failed(String operation, String key, Exception e) {
    errors.increment();
    log.warn("Entitlement cache {} failed for {}; using the database", operation, key, e);
  }

  private static Counter counter(MeterRegistry meters, String result) {
    return Counter.builder("entitlement.cache")
        .description("Entitlement row lookups by cache outcome (ADR-0016)")
        .tag("result", result)
        .register(meters);
  }
}
