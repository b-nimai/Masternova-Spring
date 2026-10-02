package com.masternova.java.oop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.oop.encapsulation.Cart;
import com.masternova.java.streams.CatalogData;
import com.masternova.java.streams.Course;
import com.masternova.java.valueobject.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CartTest {

  private final Map<String, Course> catalog =
      CatalogData.courses().stream().collect(Collectors.toMap(Course::id, Function.identity()));

  @Test
  void totalIsAlwaysDerivedFromTheItems() {
    Cart cart = new Cart("asha");
    cart.add(catalog.get("c1")); // ₹1499
    cart.add(catalog.get("c2")); // ₹999

    assertThat(cart.total()).isEqualTo(Money.of(2_498_00, "INR"));

    cart.remove("c1");
    assertThat(cart.total()).isEqualTo(Money.of(999_00, "INR"));
  }

  @Test
  void rulesAreEnforcedByTheCartItself() {
    Cart cart = new Cart("asha");
    cart.add(catalog.get("c1"));

    assertThatThrownBy(() -> cart.add(catalog.get("c1"))).hasMessageContaining("already in the cart");
    assertThatThrownBy(() -> cart.add(catalog.get("c8"))).hasMessageContaining("not available");
  }

  @Test
  void oneCurrencyPerCart() {
    Cart cart = new Cart("asha");
    cart.add(catalog.get("c1"));
    Course dollars = new Course("u1", "Go Basics", "Backend", "Sam", Money.of(1_000, "USD"), 4.0,
        10, 10, true, Set.of("go"), LocalDate.of(2026, 1, 1));

    assertThatThrownBy(() -> cart.add(dollars)).hasMessageContaining("one currency");
  }

  @Test
  void itemsCannotBeModifiedFromOutside() {
    Cart cart = new Cart("asha");
    cart.add(catalog.get("c1"));

    List<Course> snapshot = cart.items();

    assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThat(cart.items()).hasSize(1);
  }

  @Test
  void emptyCartTotalsZero() {
    assertThat(new Cart("asha").total().isZero()).isTrue();
  }
}
