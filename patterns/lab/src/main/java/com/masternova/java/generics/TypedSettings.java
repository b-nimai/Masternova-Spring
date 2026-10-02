package com.masternova.java.generics;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A type-safe heterogeneous container (Effective Java, item 33): one map holding values of
 * DIFFERENT types, keyed by their {@link Class} — {@code settings.get(Integer.class)} returns an
 * {@code Integer}, no cast at the call site.
 *
 * <p>⭐ Why a Class object? Type parameters are ERASED at runtime (note §7): inside a generic
 * method {@code T} is unknown. A {@code Class<T>} "type token" carries the type into runtime, and
 * {@link Class#cast} checks it.
 */
public final class TypedSettings {

  private final Map<Class<?>, Object> values = new HashMap<>();

  public <T> void put(Class<T> type, T value) {
    values.put(Objects.requireNonNull(type, "type"), type.cast(value)); // cast rejects raw-type tricks
  }

  public <T> T get(Class<T> type) {
    return type.cast(values.get(type)); // ⭐ checked cast via the token — no @SuppressWarnings
  }
}
