package com.masternova.patterns.strategy;

/** <b>ConcreteStrategy</b> — simulated Stripe with a per-charge limit. */
public final class StripeGateway implements PaymentGateway {

  private static final long LIMIT_MINOR = 1_000_000; // 10,000.00

  @Override
  public PaymentProvider provider() {
    return PaymentProvider.STRIPE;
  }

  @Override
  public PaymentResult charge(ChargeRequest request) {
    if (request.amountMinor() > LIMIT_MINOR) {
      return new PaymentResult.Declined("amount over demo limit");
    }
    return new PaymentResult.Approved("ch_" + request.orderId());
  }
}
