package com.masternova.api.entitlement.domain;

import static com.masternova.api.entitlement.domain.Contexts.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.domain.Entitlement.Status;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntitlementTest {

  @Test
  void statusIsComputedAtATime() {
    Entitlement rental =
        Entitlement.grant(
            UUID.randomUUID(),
            UUID.randomUUID(),
            EntitlementSource.PURCHASE,
            NOW,
            Optional.of(NOW.plus(Duration.ofDays(30))));

    assertThat(rental.status(NOW)).isEqualTo(Status.ACTIVE);
    assertThat(rental.status(NOW.plus(Duration.ofDays(30)))).isEqualTo(Status.EXPIRED); // ≤ now
  }

  @Test
  void revokedWinsOverExpired() {
    assertThat(Contexts.revoked().status(NOW.plus(Duration.ofDays(9999))))
        .isEqualTo(Status.REVOKED);
  }

  @Test
  void aGrantMustExpireAfterItStarts() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Entitlement.grant(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    EntitlementSource.ADMIN_GRANT,
                    NOW,
                    Optional.of(NOW)));
  }
}
