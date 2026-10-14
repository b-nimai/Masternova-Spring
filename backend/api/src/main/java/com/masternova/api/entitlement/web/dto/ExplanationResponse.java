package com.masternova.api.entitlement.web.dto;

import com.masternova.api.entitlement.domain.PolicyChain.Evaluation;
import com.masternova.api.entitlement.domain.Vote;
import java.util.List;

/**
 * Why a learner may or may not watch a lecture: the decision and every policy's vote, in order —
 * what support reads before answering "I paid and can't watch".
 */
public record ExplanationResponse(AccessResponse decision, List<PolicyVoteResponse> votes) {

  /** {@code vote} is ALLOW, DENY or ABSTAIN; {@code reason} is the grant or denial, if any. */
  public record PolicyVoteResponse(String policy, String vote, String reason) {}

  public static ExplanationResponse from(Evaluation e) {
    return new ExplanationResponse(
        AccessResponse.from(e.decision()),
        e.votes().stream()
            .map(
                v ->
                    switch (v.vote()) {
                      case Vote.Allow(var grant) ->
                          new PolicyVoteResponse(v.policy(), "ALLOW", grant.name());
                      case Vote.Deny(var reason) ->
                          new PolicyVoteResponse(v.policy(), "DENY", reason.name());
                      case Vote.Abstain _ -> new PolicyVoteResponse(v.policy(), "ABSTAIN", null);
                    })
            .toList());
  }
}
