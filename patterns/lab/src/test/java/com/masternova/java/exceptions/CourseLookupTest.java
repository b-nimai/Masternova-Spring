package com.masternova.java.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.streams.CatalogData;
import com.masternova.java.valueobject.Money;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CourseLookupTest {

  private final CourseLookup lookup = new CourseLookup(CatalogData.courses());

  @Test
  void presentAndEmpty() {
    assertThat(lookup.findById("c1")).isPresent();
    assertThat(lookup.findById("nope")).isEmpty();
  }

  @Test
  void orFallsBackToTheSlug() {
    assertThat(lookup.findByIdOrSlug("c2")).map(c -> c.id()).hasValue("c2");
    assertThat(lookup.findByIdOrSlug("angular-signals")).map(c -> c.id()).hasValue("c3");
    assertThat(lookup.findByIdOrSlug("missing")).isEmpty();
  }

  @Test
  void orElseThrowRaisesTheDomainException() {
    assertThatThrownBy(() -> lookup.getOrThrow("c404"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Course c404 was not found");
  }

  @Test
  void mapFilterAndFlatMap() {
    assertThat(lookup.titleOrPlaceholder("nope")).isEqualTo("(unknown course)");
    assertThat(lookup.priceIfPublished("c1")).hasValue(Money.of(1_499_00, "INR"));
    assertThat(lookup.priceIfPublished("c8")).isEmpty(); // a draft is filtered out
    assertThat(lookup.publishedOn("c1")).hasValue(LocalDate.of(2026, 1, 10));
    assertThat(lookup.publishedOn("c8")).isEmpty(); // draft: publishedOn is null → empty
  }

  @Test
  void orElseEvaluatesItsArgumentEvenWhenNotNeeded() {
    AtomicInteger calls = new AtomicInteger();

    String eager = lookup.findById("c1").map(c -> c.title()).orElse(expensiveDefault(calls));
    String lazy = lookup.findById("c1").map(c -> c.title()).orElseGet(() -> expensiveDefault(calls));

    assertThat(eager).isEqualTo(lazy);
    // ⭐ orElse(x): x is computed BEFORE the call, always. orElseGet(supplier): only if empty.
    assertThat(calls).hasValue(1);
  }

  @Test
  void getOnAnEmptyOptionalIsJustANullPointerInDisguise() {
    Optional<String> empty = Optional.empty();

    assertThatThrownBy(empty::get).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> Optional.of(null)).isInstanceOf(NullPointerException.class);
  }

  private static String expensiveDefault(AtomicInteger calls) {
    calls.incrementAndGet(); // imagine a database call here
    return "(default)";
  }
}
