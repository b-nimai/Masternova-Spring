package com.masternova.api.entitlement;

/**
 * Why access was DENIED — the {@code reason} of a 403 (API conventions §10). ⭐ The client maps it
 * to a next step (buy, renew, contact support, "no longer available") without parsing English, so
 * these names are a contract: add, never rename.
 */
public enum DenialReason {
  NO_ENTITLEMENT,
  ENTITLEMENT_EXPIRED,
  ENTITLEMENT_REVOKED,
  COURSE_NOT_PUBLISHED,
  /** A policy threw: the chain fails shut instead of answering 500 (LLD §8). */
  ACCESS_CHECK_FAILED
}
