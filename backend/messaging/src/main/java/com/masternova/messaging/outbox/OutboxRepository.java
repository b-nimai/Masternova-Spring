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

  /** A row to append — in the caller's transaction. */
  record NewMessage(
      UUID id, String type, String aggregateId, String payloadJson, Instant occurredAt) {}

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

  /** Row counts per status — for metrics (D3) and tests. */
  Map<String, Long> countByStatus();
}
