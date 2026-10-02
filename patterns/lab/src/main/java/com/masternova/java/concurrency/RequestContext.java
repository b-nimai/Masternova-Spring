package com.masternova.java.concurrency;

import java.util.concurrent.Callable;

/**
 * "Who is the current user?" without passing it through every method — with a {@link ScopedValue}
 * (final in Java 25), the modern replacement for ThreadLocal.
 *
 * <ul>
 *   <li>bound for a SCOPE (one call), automatically unbound afterwards — no leak, no remove()
 *   <li>immutable while bound — nobody can reassign it halfway through a request
 *   <li>cheap with millions of virtual threads (a ThreadLocal per virtual thread is not)
 * </ul>
 *
 * <p>Spring Security's {@code SecurityContextHolder} solves the same problem with a ThreadLocal.
 */
public final class RequestContext {

  private static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();

  private RequestContext() {}

  /** Runs {@code work} with {@code userId} as the current user. */
  public static <T> T runAs(String userId, Callable<T> work) throws Exception {
    return ScopedValue.where(CURRENT_USER, userId).call(work::call);
  }

  /** The current user, or "anonymous" outside any runAs scope. */
  public static String currentUser() {
    return CURRENT_USER.orElse("anonymous");
  }
}
