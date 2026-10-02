package com.masternova.java.generics;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Either a value ({@link Ok}) or an error ({@link Err}) — a GENERIC sealed type. Used where a
 * failure is an expected outcome (validation, a coupon that doesn't apply), not an exception.
 *
 * <p>Study note: {@code patterns/java/04-generics.md}.
 *
 * @param <T> the type of the success value — a TYPE PARAMETER, filled in by each use: {@code
 *     Result<Money>}, {@code Result<Course>}.
 */
// ⭐ A generic sealed interface: each record repeats the type parameter <T> and passes it up.
public sealed interface Result<T> {

  record Ok<T>(T value) implements Result<T> {
    public Ok {
      Objects.requireNonNull(value, "value");
    }
  }

  record Err<T>(String code, String message) implements Result<T> {
    public Err {
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(message, "message");
    }
  }

  // ⭐ GENERIC STATIC FACTORIES: the <T> before the return type declares a type parameter for
  //    this method only. Callers rarely write it — the compiler infers it from the argument or
  //    the target type:  Result<Money> r = Result.ok(price);
  static <T> Result<T> ok(T value) {
    return new Ok<>(value); // ⭐ <> "diamond": the compiler fills in <T>
  }

  static <T> Result<T> err(String code, String message) {
    return new Err<>(code, message);
  }

  /** Runs {@code action}; an IllegalArgumentException becomes an Err instead of escaping. */
  static <T> Result<T> attempt(String code, Supplier<? extends T> action) {
    try {
      return ok(action.get());
    } catch (IllegalArgumentException e) {
      return err(code, e.getMessage());
    }
  }

  /**
   * Transforms the value, keeps an error as it is.
   *
   * <p>⭐ Read the signature with PECS (§5 of the note): {@code fn} CONSUMES a T (so {@code ?
   * super T} — a Function accepting any supertype of T also works) and PRODUCES an R (so {@code ?
   * extends R} — returning any subtype of R also works). That is exactly how {@code
   * Stream.map} is declared.
   */
  default <R> Result<R> map(Function<? super T, ? extends R> fn) {
    return switch (this) {
      case Ok<T>(T value) -> ok(fn.apply(value));
      // the Err carries no T, so it can be re-typed as Result<R> by rebuilding it
      case Err<T>(String code, String message) -> err(code, message);
    };
  }

  /** Chains a step that can itself fail. map would give Result<Result<R>>; flatMap flattens. */
  default <R> Result<R> flatMap(Function<? super T, ? extends Result<? extends R>> fn) {
    return switch (this) {
      case Ok<T>(T value) -> narrow(fn.apply(value));
      case Err<T>(String code, String message) -> err(code, message);
    };
  }

  default T orElse(T fallback) {
    return this instanceof Ok<T>(T value) ? value : fallback;
  }

  default boolean isOk() {
    return this instanceof Ok<T>;
  }

  /**
   * Result is read-only (nothing ever stores a T into it), so a Result of a subtype can safely be
   * viewed as a Result of the supertype. Java can't express that "covariance" in the type, so we
   * rebuild — no unchecked cast needed.
   */
  private static <R> Result<R> narrow(Result<? extends R> result) {
    return switch (result) {
      case Ok<? extends R>(var value) -> ok(value);
      case Err<? extends R>(String code, String message) -> err(code, message);
    };
  }
}
