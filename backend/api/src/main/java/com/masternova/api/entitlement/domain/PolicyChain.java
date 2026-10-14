package com.masternova.api.entitlement.domain;

import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.AccessDecision.Allowed;
import com.masternova.api.entitlement.AccessDecision.Denied;
import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.domain.Vote.Allow;
import com.masternova.api.entitlement.domain.Vote.Deny;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs every {@link EntitlementPolicy} and reduces their votes to one {@link AccessDecision}.
 *
 * <p>⭐ CHAIN OF RESPONSIBILITY, with two deliberate departures from the textbook (LLD §7):
 *
 * <ul>
 *   <li><b>No short-circuit, deny wins.</b> The textbook chain returns the first handler that
 *       answers, so the answer depends on list order: insert "preview → allow" above "refunded →
 *       deny" and refunds silently stop working. Here every policy runs; ANY deny beats every
 *       allow. {@code @Order} only chooses which reason is reported when several agree.
 *   <li><b>No {@code next} pointer.</b> No handler may stop the chain, so the linked list would be
 *       ceremony: the chain is the injected, ordered list.
 * </ul>
 *
 * <p>⭐ Closed by default: all-abstain, an empty list, or a policy that throws all end in DENY.
 */
@Component
@DesignPattern(
    value = Pattern.CHAIN_OF_RESPONSIBILITY,
    role = "Chain: every handler votes, deny wins, fails shut",
    note = "patterns/docs/03-chain-of-responsibility.md")
public class PolicyChain {

  private static final Logger log = LoggerFactory.getLogger(PolicyChain.class);

  /** The decision with every policy's vote, in order — what an admin's "explain" shows. */
  public record Evaluation(AccessDecision decision, List<PolicyVote> votes) {
    public Evaluation {
      Objects.requireNonNull(decision, "decision");
      votes = List.copyOf(votes);
    }
  }

  public record PolicyVote(String policy, Vote vote) {}

  private final List<EntitlementPolicy> policies;

  /** Spring injects the policies sorted by their {@code @Order}. */
  public PolicyChain(List<EntitlementPolicy> policies) {
    this.policies = List.copyOf(policies);
  }

  public Evaluation evaluate(AccessContext context) {
    List<PolicyVote> votes = new ArrayList<>(policies.size());
    for (EntitlementPolicy policy : policies) {
      votes.add(new PolicyVote(policy.name(), voteSafely(policy, context)));
    }
    return new Evaluation(reduce(votes, context), votes);
  }

  private static Vote voteSafely(EntitlementPolicy policy, AccessContext context) {
    try {
      return Objects.requireNonNull(policy.decide(context), "a policy returned null");
    } catch (RuntimeException e) {
      // ⭐ one broken rule is a denial for this request, not a 500 on every playback
      log.error("Entitlement policy {} failed; counted as a denial", policy.name(), e);
      return Vote.deny(DenialReason.ACCESS_CHECK_FAILED);
    }
  }

  /** Any deny → denied; else any allow → allowed; else closed. First of each kind names it. */
  private static AccessDecision reduce(List<PolicyVote> votes, AccessContext context) {
    for (PolicyVote v : votes) {
      if (v.vote() instanceof Deny(DenialReason reason)) {
        return new Denied(reason); // ⭐ explicit denial always wins, whatever allowed it
      }
    }
    for (PolicyVote v : votes) {
      if (v.vote() instanceof Allow(var grant)) {
        return new Allowed(grant);
      }
    }
    return new Denied(fallback(context));
  }

  /** Nobody allowed: say WHY in the viewer's terms — an expired rental reads "renew", not "buy". */
  private static DenialReason fallback(AccessContext context) {
    return context.entitlementIs(Entitlement.Status.EXPIRED)
        ? DenialReason.ENTITLEMENT_EXPIRED
        : DenialReason.NO_ENTITLEMENT;
  }
}
