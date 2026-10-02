package com.masternova.java.generics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageTest {

  @Test
  void anExtraRowMeansThereIsANextPage() {
    // the query asked for limit + 1 = 4 rows and got 4
    Page<String> page = Page.fromRows(List.of("a", "b", "c", "d"), 3, item -> "after:" + item);

    assertThat(page.items()).containsExactly("a", "b", "c");
    assertThat(page.nextCursor()).isEqualTo("after:c");
    assertThat(page.hasNext()).isTrue();
  }

  @Test
  void noExtraRowMeansLastPage() {
    Page<String> page = Page.fromRows(List.of("a", "b"), 3, item -> "after:" + item);

    assertThat(page.items()).containsExactly("a", "b");
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void mapChangesTheItemTypeAndKeepsTheCursor() {
    Page<String> titles = new Page<>(List.of("Java", "Spring"), "cur");

    Page<Integer> lengths = titles.map(String::length);

    assertThat(lengths).isEqualTo(new Page<>(List.of(4, 6), "cur"));
  }

  @Test
  void emptyPageOfAnyType() {
    Page<Integer> numbers = Page.empty();
    Page<String> strings = Page.empty();

    assertThat(numbers.items()).isEmpty();
    assertThat(strings.hasNext()).isFalse();
  }

  @Test
  void itemsAreAnImmutableSnapshot() {
    Page<String> page = new Page<>(new java.util.ArrayList<>(List.of("a")), null);

    assertThatThrownBy(() -> page.items().add("b"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
