package com.masternova.worker.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A fake of the delivery state machine for unit tests — the SECOND implementation that justifies
 * the {@link EmailDeliveries} interface. (The real SQL is proven by JdbcEmailDeliveriesIT.)
 */
public final class InMemoryEmailDeliveries implements EmailDeliveries {

  public record Row(UUID id, String status, int attempts) {}

  public final Map<DeliveryKey, Row> rows = new HashMap<>();
  public boolean nextClaimInFlight;

  @Override
  public Claim claim(DeliveryKey key) {
    if (nextClaimInFlight) {
      nextClaimInFlight = false;
      return new Claim.InFlight();
    }
    Row row = rows.get(key);
    if (row == null) {
      row = new Row(UUID.randomUUID(), "SENDING", 1);
    } else if (row.status().equals("FAILED")) {
      row = new Row(row.id(), "SENDING", row.attempts() + 1);
    } else {
      return switch (row.status()) {
        case "SENT" -> new Claim.AlreadySent();
        case "SENDING" -> new Claim.InFlight();
        default -> new Claim.Final(row.status());
      };
    }
    rows.put(key, row);
    return new Claim.Claimed(row.id(), row.attempts());
  }

  private void set(UUID id, String status) {
    rows.replaceAll((k, r) -> r.id().equals(id) ? new Row(id, status, r.attempts()) : r);
  }

  @Override
  public void markSent(UUID deliveryId, String providerMessageId) {
    set(deliveryId, "SENT");
  }

  @Override
  public void markFailed(UUID deliveryId, String error) {
    set(deliveryId, "FAILED");
  }

  @Override
  public void markBounced(UUID deliveryId, String error) {
    set(deliveryId, "BOUNCED");
  }

  @Override
  public void recordSuppressed(DeliveryKey key, String reason) {
    rows.putIfAbsent(key, new Row(UUID.randomUUID(), "SUPPRESSED", 0));
  }

  public String statusOf(DeliveryKey key) {
    return rows.get(key).status();
  }
}
