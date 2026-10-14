package com.masternova.api.entitlement.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;

/**
 * One access rule — a handler of the {@link PolicyChain}. Implementations are {@code @Component}s
 * with an {@code @Order}; adding a rule is a new class, and no existing rule is reopened.
 *
 * <p>⭐ Force: rules that vary independently and keep being added, by people who won't read the
 * others. Each is a STRATEGY over the same {@link AccessContext}; the chain composes them.
 *
 * <p>Contract: synchronous, no I/O (everything is in the context), and a DENY must scope itself
 * ({@link AccessContext#isStaff()}), because a deny always wins.
 */
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "Strategy: one access rule over the context",
    note = "patterns/docs/01-strategy.md")
@DesignPattern(
    value = Pattern.CHAIN_OF_RESPONSIBILITY,
    role = "Handler",
    note = "patterns/docs/03-chain-of-responsibility.md")
public interface EntitlementPolicy {

  Vote decide(AccessContext context);

  /** Shown in an admin's explanation and in the log when the policy throws. */
  default String name() {
    return getClass().getSimpleName();
  }
}
