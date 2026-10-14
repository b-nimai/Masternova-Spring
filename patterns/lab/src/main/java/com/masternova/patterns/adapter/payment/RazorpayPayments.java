package com.masternova.patterns.adapter.payment;

import java.util.Map;

/**
 * The ADAPTER (simplified from {@code com.masternova.api.commerce.infrastructure.RazorpayGateway}):
 * our request → the vendor's field names, the vendor's answer → our id, and — the part that
 * matters — the vendor's failures → our temporary / permanent contract.
 */
public final class RazorpayPayments implements Payments {

  private final VendorHttp http;

  public RazorpayPayments(VendorHttp http) {
    this.http = http;
  }

  @Override
  public String openOrder(String ourOrderId, long amountMinor, String currency) {
    try {
      Map<String, Object> answer =
          http.post(
              "/v1/orders",
              Map.of("amount", amountMinor, "currency", currency, "receipt", ourOrderId));
      Object id = answer.get("id");
      if (!(id instanceof String s)) {
        throw new ProviderFailed("answer without an id", false);
      }
      return s;
    } catch (VendorHttp.HttpError e) {
      // ⭐ the retry decision is made ONCE, here: 5xx and 429 are "later", any other 4xx is "never"
      throw new ProviderFailed(e.getMessage(), e.status >= 500 || e.status == 429);
    } catch (VendorHttp.NoAnswer e) {
      throw new ProviderFailed(e.getMessage(), true);
    }
  }
}
