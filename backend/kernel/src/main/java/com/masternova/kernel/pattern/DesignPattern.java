package com.masternova.kernel.pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a participant in a design pattern, so the pattern is discoverable from the code
 * and the {@code patterns/README.md} catalog can be checked against it.
 *
 * <pre>{@code
 * @DesignPattern(value = Pattern.STRATEGY, role = "ConcreteStrategy",
 *                note = "patterns/docs/01-strategy.md")
 * class RazorpayGateway implements PaymentGateway { ... }
 * }</pre>
 *
 * <p>Repeatable: one class can play roles in several patterns (e.g. a Decorator that is also a
 * Repository).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(DesignPatterns.class)
public @interface DesignPattern {

  Pattern value();

  /** The GoF role this class plays, e.g. "Context", "ConcreteStrategy", "Handler". */
  String role();

  /** Repo-relative path to the pattern note, e.g. {@code patterns/docs/01-strategy.md}. */
  String note() default "";
}
