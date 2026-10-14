package com.masternova.patterns.chain;

import java.util.List;

/**
 * ❌ The textbook chain: the FIRST rule that doesn't abstain decides, the rest never run. Fine for
 * routing a request to its handler; dangerous for authorization, because the answer depends on
 * list order (see {@code ChainTest.firstMatchLetsALaterAllowDefeatARefund}).
 */
public final class FirstMatchChain {

  private final List<Rule> rules;

  public FirstMatchChain(List<Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  public Vote decide(Access access) {
    for (Rule rule : rules) {
      Vote vote = rule.decide(access);
      if (!(vote instanceof Vote.Abstain)) {
        return vote; // ⭐ short-circuit: whoever is first wins
      }
    }
    return new Vote.Deny("closed by default");
  }
}
