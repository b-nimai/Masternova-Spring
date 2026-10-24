package com.masternova.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

/** Which database failures are "busy" or "gone", and which are bugs. */
class DatabaseFailuresTest {

  @Test
  void aPoolTimeoutWithNothingBehindItIsOverload() {
    var ex =
        new CannotCreateTransactionException(
            "Could not open JPA EntityManager",
            new SQLTransientConnectionException("HikariPool-1 - Connection is not available"));
    assertThat(code(ex, "POST")).contains("OVERLOADED");
    assertThat(DatabaseFailures.classify(ex, "POST").orElseThrow().retryable())
        .as("nothing reached the database: a retry is safe, even for a write")
        .isTrue();
  }

  @Test
  void aPoolTimeoutCausedByADeadDatabaseIsUnavailableNotOverload() {
    // Hikari's timeout carries the last connection error while the primary is down (a failover)
    var ex =
        new CannotCreateTransactionException(
            "Could not open JPA EntityManager",
            new SQLTransientConnectionException(
                "HikariPool-1 - Connection is not available",
                "08001",
                new SQLException("Connection to masternova-db-rw refused", "08001")));
    assertThat(code(ex, "GET")).contains("DATABASE_UNAVAILABLE");
  }

  @Test
  void aConnectionLostMidStatementIsUnavailable() {
    var ex =
        new DataAccessResourceFailureException(
            "could not execute statement", new SQLException("I/O error", "08006"));
    assertThat(code(ex, "GET")).contains("DATABASE_UNAVAILABLE");
    assertThat(code(new SQLException("terminating", "57P01"), "GET"))
        .contains("DATABASE_UNAVAILABLE");
    assertThat(DatabaseFailures.classify(ex, "GET").orElseThrow().retryable()).isTrue();
    assertThat(DatabaseFailures.classify(ex, "POST").orElseThrow().retryable())
        .as("a write's COMMIT may have landed: its outcome is unknown")
        .isFalse();
  }

  @Test
  void otherStatesInTheSameClassesAreBugsNotRetries() {
    assertThat(code(new SQLException("protocol violation", "08P01"), "GET")).isEmpty();
    assertThat(code(new SQLException("database dropped", "57P04"), "GET")).isEmpty();
    assertThat(code(new SQLException("unique violation", "23505"), "GET")).isEmpty();
    assertThat(code(new IllegalStateException("bug"), "GET")).isEmpty();
  }

  private static Optional<String> code(Throwable ex, String method) {
    return DatabaseFailures.classify(ex, method).map(DatabaseFailures.Unavailable::code);
  }
}
