package com.masternova.java.sealed;

import com.masternova.java.sealed.Expr.Add;
import com.masternova.java.sealed.Expr.Mul;
import com.masternova.java.sealed.Expr.Neg;
import com.masternova.java.sealed.Expr.Num;
import com.masternova.java.sealed.Expr.Var;
import java.util.Map;

/**
 * Evaluate, simplify and print an {@link Expr} — each one a recursive, exhaustive switch.
 *
 * <p>⭐ This is the modern replacement for the <b>Visitor pattern</b>: instead of an {@code
 * accept(visitor)} method on every node and a visitor interface with one method per node type,
 * each operation is one function with one switch, and the compiler checks every node is handled.
 */
public final class Exprs {

  private Exprs() {}

  /** Computes the value; variables come from {@code env}. Unknown variable → IAE. */
  public static int eval(Expr expr, Map<String, Integer> env) {
    return switch (expr) {
      case Num(int value) -> value;
      case Var(String name) -> {
        // ⭐ A block in a switch EXPRESSION hands back its value with `yield` (not `return`,
        //    which would return from the whole method).
        Integer value = env.get(name);
        if (value == null) {
          throw new IllegalArgumentException("unbound variable: " + name);
        }
        yield value;
      }
      case Add(Expr left, Expr right) -> Math.addExact(eval(left, env), eval(right, env));
      case Mul(Expr left, Expr right) -> Math.multiplyExact(eval(left, env), eval(right, env));
      case Neg(Expr operand) -> Math.negateExact(eval(operand, env));
    };
  }

  /**
   * Applies algebra rules bottom-up: constant folding ({@code 2 + 3 → 5}), identities ({@code x +
   * 0 → x}, {@code x * 1 → x}), zero ({@code x * 0 → 0}) and double negation ({@code --x → x}).
   */
  public static Expr simplify(Expr expr) {
    return switch (expr) {
      case Num n -> n;
      case Var v -> v;
      // ⭐ NESTED pattern: matches a Neg whose operand is itself a Neg.
      case Neg(Neg(Expr inner)) -> simplify(inner);
      case Neg(Expr operand) -> negate(simplify(operand));
      // simplify the children FIRST, then look at the simplified pair
      case Add(Expr left, Expr right) -> add(simplify(left), simplify(right));
      case Mul(Expr left, Expr right) -> multiply(simplify(left), simplify(right));
    };
  }

  // ⭐ Java has no tuple patterns, so to match on TWO values at once, wrap them in a tiny local
  //    record and switch on that. A private nested record is a common idiom for this.
  private record Pair(Expr left, Expr right) {}

  private static Expr add(Expr left, Expr right) {
    return switch (new Pair(left, right)) {
      case Pair(Num(int a), Num(int b)) -> new Num(Math.addExact(a, b));
      case Pair(Num(int zero), Expr other) when zero == 0 -> other;
      case Pair(Expr other, Num(int zero)) when zero == 0 -> other;
      case Pair p -> new Add(p.left(), p.right());
    };
  }

  private static Expr multiply(Expr left, Expr right) {
    return switch (new Pair(left, right)) {
      case Pair(Num(int a), Num(int b)) -> new Num(Math.multiplyExact(a, b));
      case Pair(Num(int zero), Expr _) when zero == 0 -> new Num(0);
      case Pair(Expr _, Num(int zero)) when zero == 0 -> new Num(0);
      case Pair(Num(int one), Expr other) when one == 1 -> other;
      case Pair(Expr other, Num(int one)) when one == 1 -> other;
      case Pair p -> new Mul(p.left(), p.right());
    };
  }

  private static Expr negate(Expr operand) {
    return operand instanceof Num(int value) ? new Num(Math.negateExact(value)) : new Neg(operand);
  }

  /** Fully parenthesised text, e.g. {@code ((x + 2) * -3)}. */
  public static String show(Expr expr) {
    return switch (expr) {
      case Num(int value) -> Integer.toString(value);
      case Var(String name) -> name;
      case Add(Expr left, Expr right) -> "(" + show(left) + " + " + show(right) + ")";
      case Mul(Expr left, Expr right) -> "(" + show(left) + " * " + show(right) + ")";
      case Neg(Expr operand) -> "-" + show(operand);
    };
  }
}
