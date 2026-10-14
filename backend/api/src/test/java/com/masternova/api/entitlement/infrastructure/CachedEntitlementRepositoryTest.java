package com.masternova.api.entitlement.infrastructure;

import static com.masternova.api.entitlement.domain.Contexts.COURSE;
import static com.masternova.api.entitlement.domain.Contexts.LEARNER;
import static com.masternova.api.entitlement.domain.Contexts.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.masternova.api.entitlement.domain.Contexts;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Decorator's own behaviour — hits, negatives, eviction AFTER commit, failing open — against an
 * in-memory Redis and a counting fake repository. No Spring context, no database, no Redis.
 */
class CachedEntitlementRepositoryTest {

  /** The decorated subject: a map, counting how often the "database" is asked. */
  static final class FakeRepository implements EntitlementRepository {
    final Map<String, Entitlement> rows = new HashMap<>();
    int finds;

    @Override
    public Optional<Entitlement> find(UUID userId, UUID courseId) {
      finds++;
      return Optional.ofNullable(rows.get(userId + ":" + courseId));
    }

    @Override
    public boolean grant(Entitlement grant) {
      rows.put(grant.userId() + ":" + grant.courseId(), grant);
      return true;
    }

    @Override
    public boolean revoke(UUID userId, UUID courseId, Entitlement.Revocation revocation) {
      rows.put(userId + ":" + courseId, Contexts.revoked());
      return true;
    }
  }

  final Map<String, String> redisData = new HashMap<>();
  final FakeRepository db = new FakeRepository();
  final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  StringRedisTemplate redis;
  CachedEntitlementRepository cached;
  boolean redisDown;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> ops = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.get(anyString())).thenAnswer(i -> up(() -> redisData.get(i.<String>getArgument(0))));
    doAnswer(i -> up(() -> redisData.put(i.getArgument(0), i.getArgument(1))))
        .when(ops)
        .set(anyString(), anyString(), any(Duration.class));
    when(redis.delete(anyString()))
        .thenAnswer(i -> up(() -> redisData.remove(i.<String>getArgument(0)) != null));
    cached =
        new CachedEntitlementRepository(
            db, redis, JsonMapper.builder().build(), Duration.ofMinutes(5), meters);
  }

  private <T> T up(java.util.function.Supplier<T> op) {
    if (redisDown) {
      throw new RedisConnectionFailureException("redis is down");
    }
    return op.get();
  }

  @AfterEach
  void endTransaction() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private double count(String result) {
    return meters.counter("entitlement.cache", "result", result).count();
  }

  @Test
  void theSecondReadIsAHit() {
    db.grant(Contexts.active());

    assertThat(cached.find(LEARNER, COURSE)).contains(Contexts.active());
    assertThat(cached.find(LEARNER, COURSE)).contains(Contexts.active());

    assertThat(db.finds).isEqualTo(1);
    assertThat(count("miss")).isEqualTo(1);
    assertThat(count("hit")).isEqualTo(1);
  }

  @Test
  void noRowIsCachedToo() {
    assertThat(cached.find(LEARNER, COURSE)).isEmpty();
    assertThat(cached.find(LEARNER, COURSE)).isEmpty();

    assertThat(db.finds).isEqualTo(1); // ⭐ the stranger probing paid courses hits Redis only
    assertThat(redisData).containsValue(CachedEntitlementRepository.NONE);
  }

  @Test
  void theCachedCopyKeepsItsInstantsSoExpiryStillWorks() {
    // ⭐ the NestJS bug: JSON gave back expiresAt as a STRING, and expired access kept working —
    //    on the cached path only. Read twice, so the second answer comes from the cache.
    db.grant(Contexts.expired());
    cached.find(LEARNER, COURSE);
    Entitlement fromCache = cached.find(LEARNER, COURSE).orElseThrow();

    assertThat(count("hit")).isEqualTo(1);
    assertThat(fromCache).isEqualTo(Contexts.expired());
    assertThat(fromCache.status(NOW)).isEqualTo(Entitlement.Status.EXPIRED);
    assertThat(Contexts.revoked()).isEqualTo(roundTrip(Contexts.revoked()));
  }

  private Entitlement roundTrip(Entitlement e) {
    db.grant(e);
    redisData.clear();
    cached.find(e.userId(), e.courseId());
    return cached.find(e.userId(), e.courseId()).orElseThrow();
  }

  @Test
  void aWriteEvictsOnlyAfterTheCommit() {
    cached.find(LEARNER, COURSE); // caches "no row"
    TransactionSynchronizationManager.initSynchronization(); // a transaction is open

    cached.grant(Contexts.active());
    // ⭐ still cached until the commit: evicting now would let a concurrent reader re-cache the
    //    old row before our write is visible
    assertThat(redisData).containsKey(CachedEntitlementRepository.key(LEARNER, COURSE));

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertThat(redisData).isEmpty();
    assertThat(cached.find(LEARNER, COURSE)).contains(Contexts.active());
  }

  @Test
  void aRolledBackWriteEvictsNothing() {
    cached.find(LEARNER, COURSE);
    TransactionSynchronizationManager.initSynchronization();

    cached.revoke(LEARNER, COURSE, new Entitlement.Revocation(NOW, "refund"));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertThat(redisData).isNotEmpty();
  }

  @Test
  void withoutATransactionTheKeyGoesAtOnce() {
    cached.find(LEARNER, COURSE);
    cached.grant(Contexts.active());
    assertThat(redisData).isEmpty();
  }

  @Test
  void redisDownFallsThroughToTheDatabase() {
    db.grant(Contexts.active());
    redisDown = true;

    assertThat(cached.find(LEARNER, COURSE)).contains(Contexts.active());
    assertThat(cached.revoke(LEARNER, COURSE, new Entitlement.Revocation(NOW, "refund")))
        .isTrue(); // ⭐ the refund committed; a failed eviction must not turn it into a 500
    assertThat(count("error")).isEqualTo(3); // read, write-back, evict
  }

  @Test
  void anUnreadableValueIsDroppedAndTheDatabaseAnswers() {
    db.grant(Contexts.active());
    redisData.put(CachedEntitlementRepository.key(LEARNER, COURSE), "{not json");

    assertThat(cached.find(LEARNER, COURSE)).contains(Contexts.active());
    assertThat(db.finds).isEqualTo(1);
    assertThat(cached.find(LEARNER, COURSE)).contains(Contexts.active()); // re-cached, readable
    assertThat(db.finds).isEqualTo(1);
  }
}
