package com.masternova.java.exceptions;

/**
 * A Supplier whose get() may throw a CHECKED exception of type E.
 *
 * <p>⭐ java.util.function.Supplier.get() declares no exceptions, so a lambda calling a method
 * that throws IOException doesn't compile there. Declaring the exception as a TYPE PARAMETER
 * (generics, note 04) lets the compiler track it: a lambda that throws IOException makes E =
 * IOException, and the caller must handle IOException — no wrapping, no loss of checking.
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {
  T get() throws E;
}
