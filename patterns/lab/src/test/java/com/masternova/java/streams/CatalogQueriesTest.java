package com.masternova.java.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.masternova.java.valueobject.Money;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogQueriesTest {

  private static final List<Course> COURSES = CatalogData.courses();
  private static final List<Sale> SALES = CatalogData.sales();

  private static Money inr(long rupees) {
    return Money.of(rupees * 100, "INR");
  }

  @Test
  void publishedTitlesKeepCatalogOrderAndSkipDrafts() {
    List<String> titles = CatalogQueries.publishedTitles(COURSES);

    assertThat(titles).hasSize(8).doesNotContain("Terraform on AWS (draft)");
    assertThat(titles.getFirst()).isEqualTo("Spring Boot Fundamentals");
  }

  @Test
  void toListResultIsUnmodifiable() {
    List<String> titles = CatalogQueries.publishedTitles(COURSES);

    assertThatThrownBy(() -> titles.add("x")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void topRatedIgnoresCoursesWithTooFewRatings() {
    assertThat(CatalogQueries.topRated(COURSES, 3, 100))
        .containsExactly("Java Streams Deep Dive", "Kubernetes for Developers",
            "Spring Boot Fundamentals");
  }

  @Test
  void countByCategoryIsSortedByKey() {
    assertThat(CatalogQueries.countByCategory(COURSES))
        .containsExactly(Map.entry("Backend", 3L), Map.entry("DevOps", 2L),
            Map.entry("Frontend", 3L));
  }

  @Test
  void averageRatingPerInstructor() {
    Map<String, Double> averages = CatalogQueries.averageRatingByInstructor(COURSES);

    assertThat(averages.get("Asha")).isCloseTo(4.75, within(1e-9)); // (4.7 + 4.8) / 2
    assertThat(averages.get("Meera")).isCloseTo(4.3, within(1e-9));
    assertThat(averages).containsOnlyKeys("Asha", "Kabir", "Meera", "Ravi", "Zoya");
  }

  @Test
  void bestCoursePerCategory() {
    assertThat(CatalogQueries.bestCoursePerCategory(COURSES))
        .containsExactly(
            Map.entry("Backend", "Java Streams Deep Dive"),
            Map.entry("DevOps", "Kubernetes for Developers"),
            Map.entry("Frontend", "Angular Signals"));
  }

  @Test
  void partitioningAlwaysHasBothKeys() {
    Map<Boolean, List<String>> split = CatalogQueries.freeVsPaid(COURSES);

    assertThat(split.get(true)).containsExactly("Docker in Practice", "CSS Grid in a Day");
    assertThat(split.get(false)).hasSize(6);
    assertThat(CatalogQueries.freeVsPaid(List.of())).containsOnlyKeys(true, false);
  }

  @Test
  void revenueByCategoryStreamAndLoopAgree() {
    Map<String, Money> expected =
        Map.of("Backend", inr(7_995), "DevOps", inr(1_999), "Frontend", inr(2_198));

    assertThat(CatalogQueries.revenueByCategory(COURSES, SALES)).isEqualTo(expected);
    assertThat(CatalogQueries.revenueByCategoryLoop(COURSES, SALES)).isEqualTo(expected);
  }

  @Test
  void monthlyRevenueInMonthOrder() {
    assertThat(CatalogQueries.monthlyRevenue(SALES))
        .containsExactly(
            Map.entry(YearMonth.of(2026, 8), inr(3_997)),
            Map.entry(YearMonth.of(2026, 9), inr(8_195)));
  }

  @Test
  void toMapThrowsOnDuplicateKeys() {
    List<Course> duplicated = List.of(COURSES.get(0), COURSES.get(0));

    assertThatThrownBy(() -> CatalogQueries.indexById(duplicated))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate key");
  }

  @Test
  void toMapMergeFunctionKeepsTheNewestCourse() {
    Map<String, Course> newest = CatalogQueries.newestCoursePerInstructor(COURSES);

    assertThat(newest.get("Asha").title()).isEqualTo("Kubernetes for Developers");
    assertThat(newest.get("Ravi").title()).isEqualTo("System Design Basics");
  }

  @Test
  void flatMapFlattensTags() {
    assertThat(CatalogQueries.allTags(COURSES))
        .containsExactly("angular", "architecture", "aws", "css", "docker", "functional", "java",
            "kubernetes", "rxjs", "spring", "terraform", "typescript");
    assertThat(CatalogQueries.tagPopularity(COURSES))
        .containsEntry("docker", 2L)
        .containsEntry("css", 1L);
  }

  @Test
  void terminalOperations() {
    assertThat(CatalogQueries.titlesIn(COURSES, "DevOps"))
        .isEqualTo("[Docker in Practice, Kubernetes for Developers, Terraform on AWS (draft)]");
    assertThat(CatalogQueries.firstCourseBy(COURSES, "Meera")).map(Course::id).hasValue("c3");
    assertThat(CatalogQueries.firstCourseBy(COURSES, "Nobody")).isEmpty();
    assertThat(CatalogQueries.anyFree(COURSES)).isTrue();
    assertThat(CatalogQueries.allPublishedHaveTags(COURSES)).isTrue();
  }

  @Test
  void summaryStatisticsInOnePass() {
    IntSummaryStatistics stats = CatalogQueries.enrollmentStats(COURSES);

    assertThat(stats.getCount()).isEqualTo(8);
    assertThat(stats.getSum()).isEqualTo(48_400);
    assertThat(stats.getMin()).isEqualTo(900);
    assertThat(stats.getMax()).isEqualTo(15_000);
    assertThat(stats.getAverage()).isEqualTo(6_050.0);
  }

  @Test
  void teeingComputesMinAndMaxTogether() {
    assertThat(CatalogQueries.priceRange(COURSES))
        .isEqualTo(new CatalogQueries.PriceRange(Money.zero("INR"), inr(2_499)));
  }

  @Test
  void gathererWindowsIntoBatches() {
    List<List<Sale>> batches = CatalogQueries.inBatches(SALES, 3);

    assertThat(batches).extracting(List::size).containsExactly(3, 3, 2);
  }

  @Test
  void streamsAreLazyAndShortCircuit() {
    List<String> visited = new ArrayList<>();

    assertThat(CatalogQueries.firstPremiumTitle(COURSES, visited))
        .hasValue("Kubernetes for Developers");
    // c4 is the first course priced >= ₹1900 — c5..c9 are never even looked at.
    assertThat(visited).containsExactly("c1", "c2", "c3", "c4");
  }
}
