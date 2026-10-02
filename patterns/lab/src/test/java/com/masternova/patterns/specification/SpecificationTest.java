package com.masternova.patterns.specification;

import static com.masternova.patterns.specification.ListingRules.free;
import static com.masternova.patterns.specification.ListingRules.published;
import static com.masternova.patterns.specification.ListingRules.ratedAtLeast;
import static com.masternova.patterns.specification.ListingRules.titleContains;
import static com.masternova.patterns.specification.ListingRules.visibleTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecificationTest {

  static final UUID ASHA = UUID.randomUUID();
  static final Listing K8S = new Listing("Kubernetes Basics", true, 0, new BigDecimal("4.6"), ASHA);
  static final Listing AWS = new Listing("AWS Draft", false, 99900, BigDecimal.ZERO, ASHA);
  static final Listing REACT =
      new Listing("React 100% Practical", true, 49900, new BigDecimal("3.9"), UUID.randomUUID());
  static final List<Listing> ALL = List.of(K8S, AWS, REACT);

  private static List<Listing> filter(Specification<Listing> spec) {
    return ALL.stream().filter(spec::isSatisfiedBy).toList();
  }

  @Test
  void leavesCombineIntoATree() {
    assertThat(filter(published().and(free()))).containsExactly(K8S);
    assertThat(filter(free().or(ratedAtLeast(new BigDecimal("3.5"))))).containsExactly(K8S, REACT);
    assertThat(filter(published().not())).containsExactly(AWS);
  }

  @Test
  void theIdentitiesAreRight() {
    // ⭐ no filters = everything; an empty OR = nothing (the bug is returning everything)
    assertThat(filter(Specification.allOf())).containsExactlyElementsOf(ALL);
    assertThat(filter(Specification.anyOf())).isEmpty();
    assertThat(Specification.<Listing>allOf().toWhere().sql()).isEqualTo("TRUE");
    assertThat(Specification.<Listing>anyOf().toWhere().sql()).isEqualTo("FALSE");
  }

  @Test
  void theSqlFormKeepsTheNestingAndTheParameters() {
    var where = published().and(free().or(ratedAtLeast(new BigDecimal("4")))).toWhere();

    // ⭐ a AND (b OR c), never a AND b OR c
    assertThat(where.sql())
        .isEqualTo("(status = 'PUBLISHED') AND ((price_minor = 0) OR (rating_average >= ?))");
    assertThat(where.params()).containsExactly(new BigDecimal("4"));
  }

  @Test
  void deMorganHoldsInMemory() {
    var a = published();
    var b = free();
    for (Listing l : ALL) {
      assertThat(a.and(b).not().isSatisfiedBy(l)).isEqualTo(a.not().or(b.not()).isSatisfiedBy(l));
    }
  }

  @Test
  void userTextIsALiteralInBothForms() {
    assertThat(filter(titleContains("100%"))).containsExactly(REACT);
    assertThat(titleContains("100%").toWhere().params()).containsExactly("%100\\%%");
  }

  @Test
  void visibilityIsOneRuleForEveryone() {
    assertThat(filter(visibleTo(null))).containsExactly(K8S, REACT);
    assertThat(filter(visibleTo(ASHA))).containsExactly(K8S, AWS, REACT); // her draft too
    assertThat(visibleTo(ASHA).toWhere().sql())
        .isEqualTo("(status = 'PUBLISHED') OR (instructor_id = ?)");
  }
}
