package com.masternova.api.commerce.infrastructure;

import java.nio.charset.StandardCharsets;

/** Webhook bodies shaped like Razorpay's (trimmed to the fields we read). */
public final class RazorpayFixtures {

  private RazorpayFixtures() {}

  public static byte[] captured(String orderId, String paymentId, long amount) {
    return paymentEvent("payment.captured", orderId, paymentId, amount, "captured", "");
  }

  public static byte[] failed(String orderId, String paymentId) {
    return paymentEvent(
        "payment.failed",
        orderId,
        paymentId,
        49_900,
        "failed",
        ", \"error_code\": \"BAD_REQUEST_ERROR\", \"error_description\": \"Card declined\"");
  }

  public static byte[] refunded(String orderId, String paymentId, String refundId) {
    return """
        {"entity": "event", "event": "refund.processed", "contains": ["refund", "payment"],
         "payload": {
           "refund": {"entity": {"id": "%s", "entity": "refund", "amount": 49900, "currency": "INR",
                                 "payment_id": "%s", "status": "processed"}},
           "payment": {"entity": {"id": "%s", "entity": "payment", "order_id": "%s",
                                  "amount": 49900, "currency": "INR", "status": "refunded"}}},
         "created_at": 1760436000}
        """
        .formatted(refundId, paymentId, paymentId, orderId)
        .getBytes(StandardCharsets.UTF_8);
  }

  public static byte[] other(String event) {
    return ("{\"entity\": \"event\", \"event\": \"" + event + "\", \"payload\": {}}")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] paymentEvent(
      String event, String orderId, String paymentId, long amount, String status, String extra) {
    return """
        {"entity": "event", "account_id": "acc_test", "event": "%s", "contains": ["payment"],
         "payload": {"payment": {"entity": {"id": "%s", "entity": "payment", "amount": %d,
                                             "currency": "INR", "status": "%s", "order_id": "%s",
                                             "method": "card"%s}}},
         "created_at": 1760436000}
        """
        .formatted(event, paymentId, amount, status, orderId, extra)
        .getBytes(StandardCharsets.UTF_8);
  }
}
