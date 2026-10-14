package com.masternova.api.entitlement.infrastructure;

import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.ReasonedDenial;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The entitlement engine as a {@code @PreAuthorize} expression:
 * {@code @PreAuthorize("@entitlements.canWatch(authentication, #lectureId)")} on any bean method.
 *
 * <p>⭐ It returns an {@link AuthorizationResult}, not a {@code boolean} (Spring Security 6.3+): a
 * boolean would turn every denial into the same {@code INSUFFICIENT_ROLE}. The result rides in the
 * {@code AuthorizationDeniedException}, and the platform's handler reads its reason ({@link
 * ReasonedDenial}) into the 403.
 */
@Component("entitlements")
public class EntitlementExpressions {

  private final EntitlementApi entitlements;

  EntitlementExpressions(EntitlementApi entitlements) {
    this.entitlements = entitlements;
  }

  public AuthorizationResult canWatch(Authentication authentication, UUID lectureId) {
    return new EntitlementAuthorization(
        entitlements.decide(CurrentUser.from(authentication), lectureId));
  }

  /** Spring's decision, plus why it was denied. */
  static final class EntitlementAuthorization extends AuthorizationDecision
      implements ReasonedDenial {

    private final @Nullable String reason;

    EntitlementAuthorization(AccessDecision decision) {
      super(decision.isAllowed());
      this.reason = decision instanceof AccessDecision.Denied(var why) ? why.name() : null;
    }

    @Override
    public String denialReason() {
      return reason;
    }
  }
}
