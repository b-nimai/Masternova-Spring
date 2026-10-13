package com.masternova.messaging.outbox;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence for the outbox, behind an interface: the SQL lives in ONE place ({@link
 * JdbcOutboxRepository}) and the relay is written against this contract.
 */
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "Repository",
    note = "patterns/docs/16-repository-unit-of-work.md")
interface OutboxRepository {

  /** A row to append — in the caller's transaction. {@code headers} may be empty. */
  record NewMessage(
      UUID id,
      String type,
      String aggregateId,
      String payloadJson,
      Instant occurredAt,
      Map<String, String> headers) {}

  void append(NewMessage message);

  /**
   * Atomically claims up to {@code batchSize} due messages for this relay: they become PROCESSING
   * with a lease of {@code lease}, and their attempt count goes up by one. Concurrent relays never
   * receive the same row.
   */
  List<OutboxMessage> claimDue(int batchSize, Duration lease);

  void markDone(UUID id);

  void reschedule(UUID id, Instant nextAttemptAt, String error);

  void markDead(UUID id, String error);

  /** Row counts per status — for tests (a full scan: never on a scrape path). */
  Map<String, Long> countByStatus();

  /**
   * What the relay still owes: rows not yet delivered (PENDING or PROCESSING), and the earliest
   * {@code next_attempt_at} among those already DUE. Served by the partial due-index, so it stays
   * cheap enough for every scrape.
   */
  Backlog backlog();

  /**
   * @param oldestDue when the most overdue row became due; {@code null} when nothing is due. A row
   *     waiting out its retry backoff, or leased by a live relay, is not due yet.
   */
  record Backlog(long size, Instant oldestDue) {}
}
