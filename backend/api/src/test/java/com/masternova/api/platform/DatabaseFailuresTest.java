package com.masternova.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
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
    assertThat(DatabaseFailures.code(ex)).contains("OVERLOADED");
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
    assertThat(DatabaseFailures.code(ex)).contains("DATABASE_UNAVAILABLE");
  }

  @Test
  void aConnectionLostMidStatementIsUnavailable() {
    var ex =
        new DataAccessResourceFailureException(
            "could not execute statement", new SQLException("I/O error", "08006"));
    assertThat(DatabaseFailures.code(ex)).contains("DATABASE_UNAVAILABLE");
    assertThat(DatabaseFailures.code(new SQLException("terminating", "57P01")))
        .contains("DATABASE_UNAVAILABLE");
  }

  @Test
  void otherStatesInTheSameClassesAreBugsNotRetries() {
    assertThat(DatabaseFailures.code(new SQLException("protocol violation", "08P01"))).isEmpty();
    assertThat(DatabaseFailures.code(new SQLException("database dropped", "57P04"))).isEmpty();
    assertThat(DatabaseFailures.code(new SQLException("unique violation", "23505"))).isEmpty();
    assertThat(DatabaseFailures.code(new IllegalStateException("bug"))).isEmpty();
  }
}
