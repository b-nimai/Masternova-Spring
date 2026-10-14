package com.masternova.patterns.chain;

/**
 * Everything a rule may look at — fetched once, before any rule runs. (The real one is {@code
 * AccessContext}: viewer, course facts, lecture, entitlement row, now.)
 */
public record Access(
    boolean admin, boolean owner, boolean preview, boolean purchased, boolean refunded) {

  boolean isStaff() {
    return admin || owner;
  }
}
