package com.masternova.api.platform;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import java.util.Set;

/**
 * Which database failures are the database being unavailable or busy, not a bug (D5.1, D5.3): the
 * one classification shared by {@code GlobalExceptionHandler} (controllers) and servlet filters
 * that reach the database before Spring MVC does (idempotency). Part of the platform module's
 * public API, like {@link ProblemTypes}.
 */
public final class DatabaseFailures {

  /**
   * Connection exceptions and server shutdown, as Postgres reports them during a failover or a
   * restart. Exact states, not prefixes: 08P01 (protocol violation) or 57P04 (database dropped) is
   * a bug or a misconfiguration, and a "retry shortly" would hide it.
   */
  private static final Set<String> UNAVAILABLE_STATES =
      Set.of("08000", "08001", "08003", "08004", "08006", "57P01", "57P02", "57P03");

  private DatabaseFailures() {}

  /**
   * {@code DATABASE_UNAVAILABLE}: the database went away (checked first: Hikari's timeout carries
   * the connection error as its cause when the database is down); {@code OVERLOADED}: no pooled
   * connection within the pool's timeout; empty: anything else, which is a bug.
   */
  public static Optional<String> code(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql
          && sql.getSQLState()
              != null // Set.of rejects contains(null); Hikari's own timeout has none
          && UNAVAILABLE_STATES.contains(sql.getSQLState())) {
        return Optional.of("DATABASE_UNAVAILABLE");
      }
    }
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof SQLTransientConnectionException) {
        return Optional.of("OVERLOADED");
      }
    }
    return Optional.empty();
  }
}
