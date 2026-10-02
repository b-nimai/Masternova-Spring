package com.masternova.java.exceptions;

/**
 * Optimistic-concurrency failure: the client edited version {@code expected}, but the stored
 * version is now {@code actual}. Carries both numbers so the API can return them (409).
 */
public final class VersionConflictException extends MasternovaException {

  private final long expected;
  private final long actual;

  public VersionConflictException(long expected, long actual) {
    super("VERSION_CONFLICT", "expected version " + expected + " but found " + actual);
    this.expected = expected;
    this.actual = actual;
  }

  public long expected() {
    return expected;
  }

  public long actual() {
    return actual;
  }
}
