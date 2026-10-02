package com.masternova.patterns.specification;

import java.util.List;

/**
 * The SPECIFICATION pattern (Evans/Fowler), Spring-free: a business rule as a small object that can
 * be COMBINED with others and evaluated in TWO ways.
 *
 * <ul>
 *   <li>{@link #isSatisfiedBy}: in memory, against one object (validation, "can this viewer see
 *       this course?").
 *   <li>{@link #toWhere}: as SQL, so the database filters a million rows instead of Java.
 * </ul>
 *
 * <p>Spring Data's {@code Specification<T>} is the SQL half only ({@code toPredicate} over the JPA
 * Criteria API). The production catalog uses it, and keeps the one rule it needs in memory too
 * (visibility) as a twin with an agreement test. Pattern note: patterns/docs/08-specification.md.
 */
public interface Specification<T> {

  boolean isSatisfiedBy(T candidate);

  /** The same rule as a parameterised SQL condition. */
  Where toWhere();

  // ⭐ Composition returns NEW specifications (they're immutable): and/or/not build a TREE.
  default Specification<T> and(Specification<T> other) {
    return new And<>(List.of(this, other));
  }

  default Specification<T> or(Specification<T> other) {
    return new Or<>(List.of(this, other));
  }

  default Specification<T> not() {
    return new Not<>(this);
  }

  /** ⭐ AND of nothing is TRUE (the identity of AND) — "no filters" means "everything". */
  @SafeVarargs
  static <T> Specification<T> allOf(Specification<T>... specs) {
    return new And<>(List.of(specs));
  }

  /** ⭐ OR of nothing is FALSE (the identity of OR) — a common bug is returning everything. */
  @SafeVarargs
  static <T> Specification<T> anyOf(Specification<T>... specs) {
    return new Or<>(List.of(specs));
  }

  /** A SQL condition and its bind parameters, in order. Never concatenate values into SQL. */
  record Where(String sql, List<Object> params) {
    public Where {
      params = List.copyOf(params);
    }

    public static Where of(String sql, Object... params) {
      return new Where(sql, List.of(params));
    }
  }

  // ------------------------------------------------------------ composites (the Composite pattern)

  record And<T>(List<Specification<T>> parts) implements Specification<T> {
    public And {
      parts = List.copyOf(parts);
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
      return parts.stream().allMatch(p -> p.isSatisfiedBy(candidate)); // short-circuits
    }

    @Override
    public Where toWhere() {
      return join(parts, " AND ", "TRUE");
    }
  }

  record Or<T>(List<Specification<T>> parts) implements Specification<T> {
    public Or {
      parts = List.copyOf(parts);
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
      return parts.stream().anyMatch(p -> p.isSatisfiedBy(candidate));
    }

    @Override
    public Where toWhere() {
      return join(parts, " OR ", "FALSE");
    }
  }

  record Not<T>(Specification<T> inner) implements Specification<T> {
    @Override
    public boolean isSatisfiedBy(T candidate) {
      return !inner.isSatisfiedBy(candidate);
    }

    @Override
    public Where toWhere() {
      Where w = inner.toWhere();
      return new Where("NOT (" + w.sql() + ")", w.params());
    }
  }

  // ⭐ Every child is wrapped in parentheses: a AND (b OR c) must not become a AND b OR c.
  private static <T> Where join(List<Specification<T>> parts, String op, String identity) {
    if (parts.isEmpty()) {
      return Where.of(identity);
    }
    List<Where> wheres = parts.stream().map(Specification::toWhere).toList();
    String sql =
        String.join(op, wheres.stream().map(w -> "(" + w.sql() + ")").toList());
    return new Where(sql, wheres.stream().flatMap(w -> w.params().stream()).toList());
  }
}
