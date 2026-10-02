package com.masternova.java.exceptions;

import java.util.List;

/** How {@code finally} really behaves — two facts and one trap. */
public final class FinallyBehaviour {

  private FinallyBehaviour() {}

  /** ⭐ finally runs even when the try block RETURNS — after the return value is computed. */
  public static String finallyRunsAfterReturn(List<String> log) {
    try {
      log.add("try");
      return "result";
    } finally {
      log.add("finally");
    }
  }

  /**
   * ⭐ THE TRAP: a {@code return} inside {@code finally} replaces whatever the try block did —
   * including an exception, which silently disappears. Never return (or throw) from finally.
   */
  @SuppressWarnings("finally") // javac warns: "finally clause cannot complete normally"
  public static int returnInFinallySwallowsTheException() {
    try {
      throw new IllegalStateException("this exception is lost");
    } finally {
      return 42; // ❌ anti-pattern, shown only so the test can prove the exception vanishes
    }
  }
}
