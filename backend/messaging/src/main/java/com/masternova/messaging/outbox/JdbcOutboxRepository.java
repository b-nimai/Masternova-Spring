package com.masternova.messaging.outbox;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxMessage;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The outbox in plain SQL (JdbcClient, not JPA): the claim query needs Postgres-specific {@code FOR
 * UPDATE SKIP LOCKED}, and outbox rows are not domain entities. Time comes from the injected {@link
 * Clock}, so tests control "now".
 */
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "ConcreteRepository",
    note = "patterns/docs/16-repository-unit-of-work.md")
class JdbcOutboxRepository implements OutboxRepository {

  // ⭐ The heart of the relay. In ONE statement:
  //    1. pick due rows (PENDING, or PROCESSING whose lease ran out), oldest first
  //    2. FOR UPDATE SKIP LOCKED — rows another relay is claiming RIGHT NOW are skipped, not waited
  //       for: concurrent relays get disjoint batches
  //    3. mark them PROCESSING, extend the lease, count the attempt
  //    4. RETURNING hands the claimed rows back — no second query, no race
  private static final String CLAIM_DUE =
      """
      UPDATE outbox_message m
         SET status = 'PROCESSING',
             attempts = m.attempts + 1,
             next_attempt_at = :leaseUntil
       WHERE m.id IN (SELECT id
                        FROM outbox_message
                       WHERE status IN ('PENDING', 'PROCESSING')
                         AND next_attempt_at <= :now
                       ORDER BY next_attempt_at, occurred_at
                       LIMIT :batchSize
                         FOR UPDATE SKIP LOCKED)
      RETURNING m.id, m.event_type, m.aggregate_id, m.payload::text AS payload, m.occurred_at, m.attempts
      """;

  private final JdbcClient jdbc;
  private final Clock clock;

  JdbcOutboxRepository(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public void append(NewMessage message) {
    jdbc.sql(
            """
            INSERT INTO outbox_message (id, event_type, aggregate_id, payload, occurred_at, status, next_attempt_at)
            VALUES (:id, :type, :aggregateId, CAST(:payload AS jsonb), :occurredAt, 'PENDING', :occurredAt)
            """)
        .param("id", message.id())
        .param("type", message.type())
        .param("aggregateId", message.aggregateId())
        .param("payload", message.payloadJson())
        .param("occurredAt", Timestamp.from(message.occurredAt()))
        .update();
  }

  @Override
  public List<OutboxMessage> claimDue(int batchSize, Duration lease) {
    Instant now = clock.instant();
    return jdbc.sql(CLAIM_DUE)
        .param("now", Timestamp.from(now))
        .param("leaseUntil", Timestamp.from(now.plus(lease)))
        .param("batchSize", batchSize)
        .query(
            (rs, row) ->
                new OutboxMessage(
                    rs.getObject("id", UUID.class),
                    rs.getString("event_type"),
                    rs.getString("aggregate_id"),
                    rs.getString("payload"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getInt("attempts")))
        .list();
  }

  @Override
  public void markDone(UUID id) {
    jdbc.sql(
            "UPDATE outbox_message SET status = 'DONE', processed_at = :now, last_error = NULL WHERE id = :id")
        .param("now", Timestamp.from(clock.instant()))
        .param("id", id)
        .update();
  }

  @Override
  public void reschedule(UUID id, Instant nextAttemptAt, String error) {
    jdbc.sql(
            "UPDATE outbox_message SET status = 'PENDING', next_attempt_at = :next, last_error = :error WHERE id = :id")
        .param("next", Timestamp.from(nextAttemptAt))
        .param("error", error)
        .param("id", id)
        .update();
  }

  @Override
  public void markDead(UUID id, String error) {
    jdbc.sql(
            "UPDATE outbox_message SET status = 'DEAD', last_error = :error, processed_at = :now WHERE id = :id")
        .param("error", error)
        .param("now", Timestamp.from(clock.instant()))
        .param("id", id)
        .update();
  }

  @Override
  public Map<String, Long> countByStatus() {
    Map<String, Long> counts = new LinkedHashMap<>();
    jdbc.sql("SELECT status, count(*) AS n FROM outbox_message GROUP BY status ORDER BY status")
        .query((rs, row) -> counts.put(rs.getString("status"), rs.getLong("n")))
        .list();
    return counts;
  }
}
