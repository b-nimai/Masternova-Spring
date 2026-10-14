package com.masternova.patterns.chain;

/** Handler: one independent rule. A new rule is a new implementation; none is reopened. */
@FunctionalInterface
public interface Rule {

  Vote decide(Access access);

  Rule ADMIN = a -> a.admin() ? new Vote.Allow("admin") : Vote.ABSTAIN;
  Rule OWNER = a -> a.owner() ? new Vote.Allow("owner") : Vote.ABSTAIN;
  Rule PREVIEW = a -> a.preview() ? new Vote.Allow("preview") : Vote.ABSTAIN;
  Rule PURCHASED = a -> a.purchased() ? new Vote.Allow("purchased") : Vote.ABSTAIN;

  /** A DENY must scope itself (staff is never denied), because a deny always wins. */
  Rule REFUNDED = a -> !a.isStaff() && a.refunded() ? new Vote.Deny("refunded") : Vote.ABSTAIN;
}
