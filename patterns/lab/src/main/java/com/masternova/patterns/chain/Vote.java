package com.masternova.patterns.chain;

/** Three outcomes, not two: ABSTAIN is "not my business". */
public sealed interface Vote {

  record Allow(String why) implements Vote {}

  record Deny(String why) implements Vote {}

  record Abstain() implements Vote {}

  Vote ABSTAIN = new Abstain();
}
