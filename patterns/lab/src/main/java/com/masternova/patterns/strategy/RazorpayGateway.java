package com.masternova.patterns.strategy;

/** <b>ConcreteStrategy</b> — simulated Razorpay (the real one is an Adapter over its SDK). */
public final class RazorpayGateway implements PaymentGateway {

  @Override
  public PaymentProvider provider() {
    return PaymentProvider.RAZORPAY;
  }

  @Override
  public PaymentResult charge(ChargeRequest request) {
    if (!request.currency().equals("INR")) {
      return new PaymentResult.Declined("Razorpay only settles INR in this demo");
    }
    return new PaymentResult.Approved("pay_" + request.orderId());
  }
}
