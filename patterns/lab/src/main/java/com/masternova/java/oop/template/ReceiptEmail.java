package com.masternova.java.oop.template;

import com.masternova.java.valueobject.Money;
import java.util.Objects;

/** Sent after a payment. Overrides the footer hook. */
public final class ReceiptEmail extends EmailTemplate {

  private final String courseTitle;
  private final Money amount;

  public ReceiptEmail(String courseTitle, Money amount) {
    this.courseTitle = Objects.requireNonNull(courseTitle, "courseTitle");
    this.amount = Objects.requireNonNull(amount, "amount");
  }

  @Override
  public String subject() {
    return "Your receipt for " + courseTitle;
  }

  @Override
  protected String body() {
    return "You paid " + amount.display() + " for \"" + courseTitle + "\".";
  }

  @Override
  protected String footer() {
    return "Questions about this payment? Reply to this email.";
  }

  @Override
  protected String unsubscribeTopic() {
    return "receipts";
  }
}
