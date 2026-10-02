package com.masternova.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.kernel.event.DomainEvent;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.messaging.OutboxProperties;
import com.masternova.messaging.OutboxWriter;
import com.masternova.messaging.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional outbox against a real Postgres. No scheduled relay runs (relay-enabled stays
 * false); the test builds its own relay, drives relayOnce() itself and moves a controllable clock.
 * Design: docs/lld/platform-kernel.md, ADR-0008.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "masternova.outbox.max-attempts=3",
      "masternova.outbox.base-backoff=10s",
      "masternova.outbox.lease=60s"
    })
@Import({TestcontainersConfiguration.class, TransactionalOutboxIT.Config.class})
class TransactionalOutboxIT {

  static final Instant T0 = Instant.parse("2026-10-02T10:00:00Z");

  record CourseDrafted(String aggregateId, String title) implements DomainEvent {
    @Override
    public String type() {
      return "test.course-drafted.v1";
    }
  }

  /** A clock the test can move forward. */
  static final class MutableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(T0);

    void advance(Duration duration) {
      now.updateAndGet(i -> i.plus(duration));
    }

    void reset() {
      now.set(T0);
    }

    @Override
    public Instant instant() {
      return now.get();
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  /** A handler that fails its first N deliveries. */
  static final class FlakyHandler implements OutboxHandler {
    final List<OutboxMessage> received = new ArrayList<>();
    final AtomicInteger failuresLeft = new AtomicInteger();

    @Override
    public String eventType() {
      return "test.course-drafted.v1";
    }

    @Override
    public void handle(OutboxMessage message) {
      received.add(message);
      if (failuresLeft.getAndDecrement() > 0) {
        throw new IllegalStateException("search cluster unavailable");
      }
    }
  }

  /** A business service: changes state and appends its event, in ONE transaction. */
  static class CourseService {
    private final OutboxWriter outbox;

    CourseService(OutboxWriter outbox) {
      this.outbox = outbox;
    }

    @Transactional
    public void draft(String id) {
      // (the real module would INSERT the course row here, in the same transaction)
      outbox.append(new CourseDrafted(id, "Course " + id));
    }

    @Transactional
    public void draftThenFail(String id) {
      outbox.append(new CourseDrafted(id, "Course " + id));
      throw new IllegalStateException("validation failed after publishing");
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }

    @Bean
    FlakyHandler flakyHandler() {
      return new FlakyHandler();
    }

    @Bean
    CourseService courseService(OutboxWriter outbox) {
      return new CourseService(outbox);
    }

    /** The relay the worker would run — built by hand, so no schedule competes with the test. */
    @Bean
    OutboxRelay testRelay(
        OutboxRepository repository,
        FlakyHandler handler,
        OutboxProperties settings,
        MutableClock clock) {
      return new OutboxRelay(repository, List.of(handler), settings, clock);
    }
  }

  @Autowired CourseService courses;
  @Autowired OutboxRelay relay;
  @Autowired OutboxRepository repository;
  @Autowired FlakyHandler handler;
  @Autowired MutableClock clock;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void reset() {
    jdbc.sql("DELETE FROM outbox_message").update();
    clock.reset();
    handler.received.clear();
    handler.failuresLeft.set(0);
  }

  private String statusOf(String aggregateId) {
    return jdbc.sql("SELECT status FROM outbox_message WHERE aggregate_id = :id")
        .param("id", aggregateId)
        .query(String.class)
        .single();
  }

  @Test
  void aCommittedChangeLeavesAPendingRowWithTheEventAsJson() {
    courses.draft("c1");

    assertThat(statusOf("c1")).isEqualTo("PENDING");
    String payload =
        jdbc.sql("SELECT payload::text FROM outbox_message WHERE aggregate_id = 'c1'")
            .query(String.class)
            .single();
    assertThat(payload).contains("\"aggregateId\"").contains("\"title\"").contains("Course c1");
  }

  @Test
  void aRolledBackChangeLeavesNoRowAtAll() {
    assertThatThrownBy(() -> courses.draftThenFail("c2")).isInstanceOf(IllegalStateException.class);

    // ⭐ THE point of the pattern: no state change → no event. No ghost messages, ever.
    assertThat(repository.countByStatus()).isEmpty();
  }

  @Test
  void theRelayDeliversAndMarksDone() {
    courses.draft("c3");

    OutboxRelay.BatchResult result = relay.relayOnce();

    assertThat(result.delivered()).isEqualTo(1);
    assertThat(handler.received)
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.type()).isEqualTo("test.course-drafted.v1");
              assertThat(m.attempts()).isEqualTo(1);
            });
    assertThat(statusOf("c3")).isEqualTo("DONE");
    assertThat(relay.relayOnce().total()).isZero(); // nothing left to claim
  }

  @Test
  void failuresAreRetriedWithBackoffThenParkedAsDead() {
    handler.failuresLeft.set(99); // always fails
    courses.draft("c4");

    assertThat(relay.relayOnce().retried()).isEqualTo(1); // attempt 1 → retry in 10 s
    assertThat(relay.relayOnce().total()).isZero(); //        not due yet
    clock.advance(Duration.ofSeconds(10));
    assertThat(relay.relayOnce().retried()).isEqualTo(1); // attempt 2 → retry in 20 s
    clock.advance(Duration.ofSeconds(20));
    assertThat(relay.relayOnce().dead()).isEqualTo(1); //    attempt 3 = max-attempts → DEAD

    assertThat(statusOf("c4")).isEqualTo("DEAD");
    assertThat(handler.received).hasSize(3);
  }

  @Test
  void aTransientFailureRecoversOnRetry() {
    handler.failuresLeft.set(1);
    courses.draft("c5");

    relay.relayOnce();
    clock.advance(Duration.ofSeconds(10));
    assertThat(relay.relayOnce().delivered()).isEqualTo(1);

    assertThat(statusOf("c5")).isEqualTo("DONE");
  }

  @Test
  void aCrashedRelaysLeaseExpiresAndTheMessageIsReclaimed() {
    courses.draft("c6");
    List<OutboxMessage> claimed = repository.claimDue(10, Duration.ofSeconds(60));
    assertThat(claimed).hasSize(1); // a relay took it … and then "crashed" (never marked it)

    assertThat(repository.claimDue(10, Duration.ofSeconds(60))).isEmpty(); // leased: hands off
    clock.advance(Duration.ofSeconds(61));

    List<OutboxMessage> reclaimed = repository.claimDue(10, Duration.ofSeconds(60));
    assertThat(reclaimed).singleElement().satisfies(m -> assertThat(m.attempts()).isEqualTo(2));
  }

  @Test
  void concurrentRelaysNeverClaimTheSameMessage() throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      courses.draft("bulk-" + i);
    }
    Set<UUID> claimedIds = ConcurrentHashMap.newKeySet();
    AtomicInteger duplicates = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);

    try (var relays = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int r = 0; r < 4; r++) { // four relay instances polling at the same moment
        relays.submit(
            () -> {
              start.await();
              List<OutboxMessage> batch;
              while (!(batch = repository.claimDue(7, Duration.ofSeconds(60))).isEmpty()) {
                for (OutboxMessage m : batch) {
                  if (!claimedIds.add(m.id())) {
                    duplicates.incrementAndGet();
                  }
                }
              }
              return null;
            });
      }
      start.countDown();
    }

    // ⭐ FOR UPDATE SKIP LOCKED: every message claimed exactly once across all relays
    assertThat(duplicates).hasValue(0);
    assertThat(claimedIds).hasSize(200);
  }

  @Test
  void backoffDoublesAndIsCapped() {
    assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(10));
    assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(20));
    assertThat(relay.backoff(3)).isEqualTo(Duration.ofSeconds(40));
    assertThat(relay.backoff(40)).isEqualTo(Duration.ofHours(1)); // capped at max-backoff
  }
}
