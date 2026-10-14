package com.masternova.api.entitlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.domain.Entitlement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SQL alone, against the real schema (V17) — no cache in front: the decorator's behaviour has
 * its own unit test. ⭐ Not transactional: the concurrency proof needs committed rows that 50
 * threads can see.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TestcontainersConfiguration.class)
class JdbcEntitlementRepositoryIT {

  static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS); // timestamptz precision

  @Autowired DataSource dataSource;
  JdbcEntitlementRepository repository;
  UUID learner;
  UUID course;

  @BeforeEach
  void setUp() {
    repository = new JdbcEntitlementRepository(JdbcClient.create(dataSource));
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course =
        new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null).courseId();
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  private Entitlement lifetime() {
    return Entitlement.grant(learner, course, EntitlementSource.PURCHASE, NOW, Optional.empty());
  }

  private Entitlement rental(Duration length) {
    return Entitlement.grant(
        learner, course, EntitlementSource.PURCHASE, NOW, Optional.of(NOW.plus(length)));
  }

  @Test
  void aGrantRoundTrips() {
    assertThat(repository.find(learner, course)).isEmpty();

    assertThat(repository.grant(rental(Duration.ofDays(30)))).isTrue();

    assertThat(repository.find(learner, course)).contains(rental(Duration.ofDays(30)));
  }

  @Test
  void aDuplicateGrantChangesNothing() {
    repository.grant(lifetime());
    Entitlement later =
        Entitlement.grant(
            learner, course, EntitlementSource.ADMIN_GRANT, NOW.plusSeconds(60), Optional.empty());

    assertThat(repository.grant(later)).isFalse(); // a webhook redelivery: no-op
    assertThat(repository.find(learner, course)).contains(lifetime()); // first grant kept
  }

  @Test
  void aGrantNeverShortensAccess() {
    repository.grant(lifetime());
    assertThat(repository.grant(rental(Duration.ofDays(30)))).isFalse();

    repository.revoke(learner, course, new Entitlement.Revocation(NOW, "test reset"));
    repository.grant(rental(Duration.ofDays(30))); // re-granted after the revoke
    assertThat(repository.grant(rental(Duration.ofDays(10)))).isFalse(); // shorter: ignored
    assertThat(repository.grant(rental(Duration.ofDays(60)))).isTrue(); // longer: extends
    assertThat(repository.grant(lifetime())).isTrue(); // lifetime outlasts any rental
    assertThat(repository.find(learner, course).orElseThrow().expiresAt()).isEmpty();
  }

  @Test
  void revokeIsOnceAndAGrantAfterItRestoresAccess() {
    repository.grant(lifetime());
    Entitlement.Revocation refund = new Entitlement.Revocation(NOW.plusSeconds(5), "refund");

    assertThat(repository.revoke(learner, course, refund)).isTrue();
    assertThat(repository.revoke(learner, course, refund)).isFalse(); // already revoked
    assertThat(repository.find(learner, course).orElseThrow().revocation()).contains(refund);

    assertThat(repository.grant(lifetime())).isTrue(); // bought again after the refund
    assertThat(repository.find(learner, course).orElseThrow().status(NOW))
        .isEqualTo(Entitlement.Status.ACTIVE);
  }

  @Test
  void revokingNothingIsFalse() {
    assertThat(repository.revoke(learner, course, new Entitlement.Revocation(NOW, "refund")))
        .isFalse();
  }

  @Test
  void fiftyConcurrentCopiesOfOneGrantMakeOneRow() throws Exception {
    // ⭐ what a payment webhook delivered 50 times at once looks like: one upsert per thread
    int copies = 50;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(copies)) {
      for (int i = 0; i < copies; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return repository.grant(lifetime());
                }));
      }
      start.countDown();
      int changed = 0;
      for (Future<Boolean> r : results) {
        changed += r.get() ? 1 : 0;
      }
      assertThat(changed).isEqualTo(1); // exactly one copy did anything
    }
    assertThat(
            JdbcClient.create(dataSource)
                .sql("SELECT count(*) FROM entitlement WHERE course_id = :course")
                .param("course", course)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }
}
