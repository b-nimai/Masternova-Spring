package com.masternova.java.sealed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.sealed.Expr.Add;
import com.masternova.java.sealed.Expr.Mul;
import com.masternova.java.sealed.Expr.Neg;
import com.masternova.java.sealed.Expr.Num;
import com.masternova.java.sealed.Expr.Var;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExprsTest {

  private static final Var X = new Var("x");

  @Test
  void evaluatesWithVariables() {
    Expr expr = new Mul(new Add(X, new Num(2)), new Neg(new Num(3))); // (x + 2) * -3

    assertThat(Exprs.eval(expr, Map.of("x", 4))).isEqualTo(-18);
    assertThat(Exprs.show(expr)).isEqualTo("((x + 2) * -3)");
  }

  @Test
  void unboundVariableIsAnError() {
    assertThatThrownBy(() -> Exprs.eval(X, Map.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void foldsConstants() {
    Expr expr = new Add(new Num(2), new Mul(new Num(3), new Num(4)));

    assertThat(Exprs.simplify(expr)).isEqualTo(new Num(14));
  }

  @Test
  void removesIdentitiesAndZeros() {
    assertThat(Exprs.simplify(new Add(X, new Num(0)))).isEqualTo(X);
    assertThat(Exprs.simplify(new Mul(new Num(1), X))).isEqualTo(X);
    assertThat(Exprs.simplify(new Mul(X, new Num(0)))).isEqualTo(new Num(0));
    assertThat(Exprs.simplify(new Neg(new Neg(X)))).isEqualTo(X);
  }

  @Test
  void simplifiesBottomUp() {
    // ((x * 1) + (2 - 2 written as 2 + -2)) → x
    Expr expr = new Add(new Mul(X, new Num(1)), new Add(new Num(2), new Neg(new Num(2))));

    assertThat(Exprs.simplify(expr)).isEqualTo(X);
  }

  @Test
  void simplifyNeverChangesTheValue() {
    Expr expr = new Mul(new Add(X, new Num(0)), new Add(new Num(1), new Num(2)));
    Map<String, Integer> env = Map.of("x", 7);

    assertThat(Exprs.eval(Exprs.simplify(expr), env)).isEqualTo(Exprs.eval(expr, env));
  }
}
