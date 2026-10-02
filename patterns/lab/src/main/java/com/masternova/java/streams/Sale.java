package com.masternova.java.streams;

import com.masternova.java.valueobject.Money;
import java.time.LocalDate;
import java.util.Objects;

/** One purchase of a course. */
public record Sale(String courseId, Money amount, LocalDate date) {

  public Sale {
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(date, "date");
  }
}
