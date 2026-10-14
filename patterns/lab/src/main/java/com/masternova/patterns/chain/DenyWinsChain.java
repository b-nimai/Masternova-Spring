package com.masternova.patterns.chain;

import java.util.List;

/**
 * The chain Masternova ships ({@code PolicyChain}): EVERY rule votes; any deny beats every allow;
 * nobody allowing — or an empty chain, or a throwing rule — is a deny. Order only picks which
 * reason is reported.
 */
public final class DenyWinsChain {

  private final List<Rule> rules;

  public DenyWinsChain(List<Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  public Vote decide(Access access) {
    List<Vote> votes = rules.stream().map(r -> safely(r, access)).toList();
    return votes.stream()
        .filter(v -> v instanceof Vote.Deny)
        .findFirst()
        .or(() -> votes.stream().filter(v -> v instanceof Vote.Allow).findFirst())
        .orElse(new Vote.Deny("closed by default"));
  }

  private static Vote safely(Rule rule, Access access) {
    try {
      return rule.decide(access);
    } catch (RuntimeException e) {
      return new Vote.Deny("rule failed"); // a broken rule fails shut, not with a 500
    }
  }
}
