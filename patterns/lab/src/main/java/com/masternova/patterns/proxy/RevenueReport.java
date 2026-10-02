package com.masternova.patterns.proxy;

/** <b>Subject</b> — an instructor's earnings, in minor units. */
public interface RevenueReport {
  long totalFor(String instructorId);
}
