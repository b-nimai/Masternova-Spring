package com.masternova.api.entitlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.TestCourses.Seeded;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ⭐ Cache invalidation against the REAL Redis and Postgres (ADR-0016): what's cached, when it goes,
 * and what a write behind the cache's back costs. Read through the module API, as commerce will.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EntitlementCacheIT {

  @Autowired EntitlementApi entitlements;
  @Autowired StringRedisTemplate redis;
  @Autowired TransactionTemplate transactions;
  @Autowired DataSource dataSource;
  @Autowired MeterRegistry meters;
  UUID learner;
  Seeded course;
  Optional<CurrentUser> me;
  String key;

  @BeforeEach
  void setUp() {
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null);
    me = Optional.of(new CurrentUser(learner, Set.of(Role.LEARNER), true));
    key = CachedEntitlementRepository.key(learner, course.courseId());
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
    redis.delete(key);
  }

  private AccessDecision decide() {
    return entitlements.decide(me, course.paidLecture());
  }

  private double hits() {
    return meters.counter("entitlement.cache", "result", "hit").count();
  }

  @Test
  void aDecisionCachesTheRowNotTheDecisionAndExpires() {
    decide();

    assertThat(redis.opsForValue().get(key)).isEqualTo(CachedEntitlementRepository.NONE);
    assertThat(redis.getExpire(key)).isBetween(1L, Duration.ofMinutes(5).toSeconds());
    double before = hits();
    decide();
    assertThat(hits()).isEqualTo(before + 1); // the second decision didn't touch Postgres
  }

  @Test
  void aGrantEvictsAfterItsCommitAndTheNextDecisionSeesIt() {
    assertThat(decide()).isEqualTo(new AccessDecision.Denied(DenialReason.NO_ENTITLEMENT));

    entitlements.grant(learner, course.courseId(), EntitlementSource.PURCHASE, Optional.empty());

    assertThat(redis.hasKey(key)).isFalse();
    assertThat(decide()).isEqualTo(new AccessDecision.Allowed(AccessGrant.ENTITLED));
    assertThat(redis.opsForValue().get(key)).contains("\"PURCHASE\""); // the row, as JSON
  }

  @Test
  void aRevokeEvictsTooSoARefundTakesEffectAtTheNextDecision() {
    entitlements.grant(learner, course.courseId(), EntitlementSource.PURCHASE, Optional.empty());
    assertThat(decide().isAllowed()).isTrue(); // cached as ACTIVE

    entitlements.revoke(learner, course.courseId(), "refund");

    assertThat(decide()).isEqualTo(new AccessDecision.Denied(DenialReason.ENTITLEMENT_REVOKED));
  }

  @Test
  void aRolledBackGrantEvictsNothing() {
    decide(); // caches "no row"

    transactions.executeWithoutResult(
        tx -> {
          entitlements.grant(
              learner, course.courseId(), EntitlementSource.PURCHASE, Optional.empty());
          tx.setRollbackOnly(); // e.g. the order insert after it failed
        });

    assertThat(redis.opsForValue().get(key)).isEqualTo(CachedEntitlementRepository.NONE);
    assertThat(decide().isAllowed()).isFalse();
  }

  @Test
  void insideALongerTransactionTheKeyGoesOnlyAtTheCommit() {
    decide();

    transactions.executeWithoutResult(
        tx -> {
          entitlements.grant(
              learner, course.courseId(), EntitlementSource.PURCHASE, Optional.empty());
          // ⭐ still cached: until the commit, the old row IS what other readers see
          assertThat(redis.hasKey(key)).isTrue();
        });

    assertThat(redis.hasKey(key)).isFalse();
  }

  @Test
  void aWriteBehindTheCachesBackStaysStaleUntilTheTtl() {
    decide(); // "no row" cached

    // ❌ not through the repository: the decorator never hears of it
    JdbcClient.create(dataSource)
        .sql(
            "INSERT INTO entitlement (user_id, course_id, source, granted_at)"
                + " VALUES (:user, :course, 'ADMIN_GRANT', now())")
        .param("user", learner)
        .param("course", course.courseId())
        .update();

    assertThat(decide().isAllowed()).isFalse(); // ⭐ why every writer is a repository method
    redis.delete(key); // what the TTL does, eventually
    assertThat(decide().isAllowed()).isTrue();
  }
}
