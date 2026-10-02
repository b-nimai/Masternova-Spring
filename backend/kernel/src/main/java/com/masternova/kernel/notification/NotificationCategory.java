package com.masternova.kernel.notification;

/**
 * Why an email is sent — the unit users consent to (docs/lld/notification.md §3). Lives in the
 * kernel because BOTH deployables use it: the api stores preferences per category, the worker
 * checks them before sending. One enum = the two sides can't disagree on what exists.
 */
public enum NotificationCategory {
  /** Verification, password reset, "your password changed". Never opt-out-able. */
  ACCOUNT_SECURITY(true),
  /** Receipts, refunds, enrollment — part of the transaction itself. */
  PURCHASE(true),
  /** Transcode finished/failed, course published. */
  COURSE_ACTIVITY(false),
  /** Reviews, Q&A answers. */
  ENGAGEMENT(false),
  /** Announcements and the welcome email. */
  PRODUCT_NEWS(false);

  private final boolean mandatory;

  NotificationCategory(boolean mandatory) {
    this.mandatory = mandatory;
  }

  /** Mandatory categories ignore preferences (but never a suppressed address). */
  public boolean mandatory() {
    return mandatory;
  }
}
