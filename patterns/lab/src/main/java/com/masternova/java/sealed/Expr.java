package com.masternova.java.sealed;

import java.util.Objects;

/**
 * A tiny arithmetic expression tree: {@code (x + 0) * 1} is {@code Mul(Add(Var("x"), Num(0)),
 * Num(1))}. The classic example of an <b>algebraic data type</b> — a sealed interface whose
 * records may contain other {@code Expr}s (a recursive structure).
 *
 * <p>Operations live in {@link Exprs}. In Masternova the same shape appears as the search-filter
 * tree (Phase 5) and curriculum commands (Phase 6).
 */
public sealed interface Expr {

  record Num(int value) implements Expr {}

  record Var(String name) implements Expr {
    public Var {
      Objects.requireNonNull(name, "name");
    }
  }

  record Add(Expr left, Expr right) implements Expr {}

  record Mul(Expr left, Expr right) implements Expr {}

  record Neg(Expr operand) implements Expr {}
}
