package com.masternova.java.generics;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.generics.Result.Err;
import com.masternova.java.generics.Result.Ok;
import com.masternova.java.valueobject.Money;
import org.junit.jupiter.api.Test;

class ResultTest {

  @Test
  void mapTransformsOkAndPassesErrThrough() {
    Result<Money> price = Result.ok(Money.of(1_499_00, "INR"));
    Result<Money> broken = Result.err("NO_PRICE", "course has no price");

    assertThat(price.map(Money::display)).isEqualTo(new Ok<>("INR 1499.00"));
    assertThat(broken.map(Money::display)).isEqualTo(new Err<>("NO_PRICE", "course has no price"));
  }

  @Test
  void flatMapChainsStepsThatCanFail() {
    Result<String> code = Result.ok("SAVE20");

    Result<Integer> percent = code.flatMap(ResultTest::lookupCoupon);
    Result<Integer> missing = Result.<String>ok("NOPE").flatMap(ResultTest::lookupCoupon);

    assertThat(percent).isEqualTo(new Ok<>(20));
    assertThat(missing).isEqualTo(new Err<>("UNKNOWN_COUPON", "NOPE"));
  }

  @Test
  void attemptTurnsAnExceptionIntoAnErr() {
    Result<Money> bad = Result.attempt("BAD_AMOUNT", () -> Money.parse("1.005", "INR"));
    Result<Money> good = Result.attempt("BAD_AMOUNT", () -> Money.parse("10", "INR"));

    assertThat(bad.isOk()).isFalse();
    assertThat(bad).isInstanceOf(Err.class);
    assertThat(good.orElse(Money.zero("INR"))).isEqualTo(Money.of(10_00, "INR"));
  }

  @Test
  void orElseOnlyUsedForErr() {
    assertThat(Result.<Integer>err("X", "y").orElse(0)).isZero();
    assertThat(Result.ok(5).orElse(0)).isEqualTo(5);
  }

  @Test
  void wildcardsLetBroaderFunctionsFit() {
    // map takes Function<? super T, ? extends R>: a function on Object (a SUPERtype of Money)
    // returning Integer (a SUBtype of Number) fits a Result<Money> → Result<Number>.
    java.util.function.Function<Object, Integer> hash = Object::hashCode;
    Result<Number> hashed = Result.ok(Money.of(1, "INR")).<Number>map(hash);

    assertThat(hashed.isOk()).isTrue();
  }

  private static Result<Integer> lookupCoupon(String code) {
    return code.equals("SAVE20") ? Result.ok(20) : Result.err("UNKNOWN_COUPON", code);
  }
}
