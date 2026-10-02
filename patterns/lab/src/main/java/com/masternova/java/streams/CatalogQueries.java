package com.masternova.java.streams;

import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.maxBy;
import static java.util.stream.Collectors.minBy;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.teeing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.masternova.java.valueobject.Money;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Gatherers;

/**
 * Catalog questions answered with streams — one technique per method.
 *
 * <p>Study note: {@code patterns/java/03-collections-and-streams.md}. Data: {@link CatalogData}.
 */
public final class CatalogQueries {

  private static final Money ZERO_INR = Money.zero("INR");

  private CatalogQueries() {}

  // ======================================================================= the basic pipeline

  /** Titles of published courses, in catalog order. */
  public static List<String> publishedTitles(List<Course> courses) {
    // ⭐ source → intermediate ops (lazy) → ONE terminal op (runs the pipeline)
    return courses.stream() //            source
        .filter(Course::published) //     intermediate: keep matching elements
        .map(Course::title) //            intermediate: transform each element
        .toList(); //                     terminal: Java 16+, returns an UNMODIFIABLE list
  }

  /** The {@code n} best-rated published courses with at least {@code minRatings} ratings. */
  public static List<String> topRated(List<Course> courses, int n, int minRatings) {
    return courses.stream()
        .filter(Course::published)
        .filter(c -> c.ratingCount() >= minRatings) // ignore 5★ from 3 votes
        // ⭐ Comparator building blocks: comparing/comparingDouble, reversed(), thenComparing()
        //    The tie-breaker makes the order DETERMINISTIC — tests and UIs need that.
        .sorted(Comparator.comparingDouble(Course::rating).reversed().thenComparing(Course::title))
        .limit(n) // short-circuits: stops after n
        .map(Course::title)
        .toList();
  }

  // ======================================================================= grouping

  /** How many published courses each category has, categories alphabetically. */
  public static Map<String, Long> countByCategory(List<Course> courses) {
    // ⭐ groupingBy(classifier, mapFactory, downstream):
    //      classifier  — the key for each element
    //      mapFactory  — which Map to build (TreeMap = sorted keys; default is HashMap = no order)
    //      downstream  — what to do with each group (here: count it)
    return courses.stream()
        .filter(Course::published)
        .collect(groupingBy(Course::category, TreeMap::new, counting()));
  }

  /** Average course rating per instructor (published, rated courses only). */
  public static Map<String, Double> averageRatingByInstructor(List<Course> courses) {
    return courses.stream()
        .filter(Course::published)
        .filter(c -> c.ratingCount() > 0)
        .collect(groupingBy(Course::instructor, TreeMap::new, averagingDouble(Course::rating)));
  }

  /** The highest-rated published course per category → its title. */
  public static Map<String, String> bestCoursePerCategory(List<Course> courses) {
    return courses.stream()
        .filter(Course::published)
        .collect(
            groupingBy(
                Course::category,
                TreeMap::new,
                // ⭐ maxBy yields Optional<Course> (a group COULD be empty in general);
                //    collectingAndThen post-processes each group's result.
                collectingAndThen(
                    maxBy(Comparator.comparingDouble(Course::rating)),
                    best -> best.orElseThrow().title())));
  }

  /** Published courses split into free (true) and paid (false). */
  public static Map<Boolean, List<String>> freeVsPaid(List<Course> courses) {
    // ⭐ partitioningBy = groupingBy with a boolean key; BOTH keys are always present.
    return courses.stream()
        .filter(Course::published)
        .collect(
            partitioningBy(
                Course::isFree,
                // ⭐ mapping(fn, downstream): transform each element BEFORE the downstream
                //    collects it — here Course → title, then into an unmodifiable list.
                collectingAndThen(mapping(Course::title, toList()), List::copyOf)));
  }

  // ======================================================================= joining two lists

  /** Revenue per category: each sale's course looked up by id, amounts summed as Money. */
  public static Map<String, Money> revenueByCategory(List<Course> courses, List<Sale> sales) {
    // ⭐ Build an index ONCE (O(n)), then look up per sale (O(1)) — never search the course
    //    list inside the sales loop (O(n × m)).
    Map<String, Course> courseById = indexById(courses);

    return sales.stream()
        .collect(
            groupingBy(
                sale -> courseById.get(sale.courseId()).category(),
                TreeMap::new,
                // ⭐ reducing(identity, mapper, combiner): sum Money with Money::plus.
                //    (summingLong would work on raw paise, but we want to stay in Money.)
                reducing(ZERO_INR, Sale::amount, Money::plus)));
  }

  /** The same answer with a plain loop and {@code Map.merge} — compare the two styles. */
  public static Map<String, Money> revenueByCategoryLoop(List<Course> courses, List<Sale> sales) {
    Map<String, Course> courseById = indexById(courses);
    Map<String, Money> revenue = new TreeMap<>();
    for (Sale sale : sales) {
      String category = courseById.get(sale.courseId()).category();
      // ⭐ merge(key, value, fn): absent → put value; present → put fn(old, value).
      revenue.merge(category, sale.amount(), Money::plus);
    }
    return revenue;
  }

  /** Revenue per calendar month, oldest first. */
  public static Map<YearMonth, Money> monthlyRevenue(List<Sale> sales) {
    return sales.stream()
        .collect(
            groupingBy(
                sale -> YearMonth.from(sale.date()),
                TreeMap::new,
                reducing(ZERO_INR, Sale::amount, Money::plus)));
  }

  /** id → course. Duplicate ids throw {@link IllegalStateException}. */
  public static Map<String, Course> indexById(List<Course> courses) {
    // ⭐ toMap(keyFn, valueFn) THROWS on a duplicate key — usually what you want for ids.
    //    Function.identity() = c -> c.
    return courses.stream().collect(toMap(Course::id, Function.identity()));
  }

  /** Each instructor's most recently published course. */
  public static Map<String, Course> newestCoursePerInstructor(List<Course> courses) {
    return courses.stream()
        .filter(Course::published)
        .collect(
            toMap(
                Course::instructor,
                Function.identity(),
                // ⭐ The 3rd argument is the MERGE function for duplicate keys — here "keep the
                //    newer course". Without it, a second course by the same instructor throws.
                BinaryOperator.maxBy(Comparator.comparing(Course::publishedOn)),
                TreeMap::new));
  }

  // ======================================================================= flatMap

  /** Every tag used by any course, each once, alphabetically. */
  public static List<String> allTags(List<Course> courses) {
    // ⭐ flatMap: each course → a stream of its tags; the streams are flattened into one.
    //    map would give Stream<Set<String>> (a stream of sets); flatMap gives Stream<String>.
    return courses.stream().flatMap(c -> c.tags().stream()).distinct().sorted().toList();
  }

  /** How many courses use each tag. */
  public static Map<String, Long> tagPopularity(List<Course> courses) {
    return courses.stream()
        .flatMap(c -> c.tags().stream())
        .collect(groupingBy(Function.identity(), TreeMap::new, counting()));
  }

  // ======================================================================= terminal ops

  /** Titles in one category as {@code "[A, B, C]"}. */
  public static String titlesIn(List<Course> courses, String category) {
    return courses.stream()
        .filter(c -> c.category().equals(category))
        .map(Course::title)
        .sorted()
        .collect(joining(", ", "[", "]")); // ⭐ joining(delimiter, prefix, suffix)
  }

  /** The first published course by an instructor, if any. */
  public static Optional<Course> firstCourseBy(List<Course> courses, String instructor) {
    // ⭐ findFirst returns Optional — "there may be no answer" is in the TYPE, not a null.
    return courses.stream()
        .filter(Course::published)
        .filter(c -> c.instructor().equals(instructor))
        .findFirst();
  }

  public static boolean anyFree(List<Course> courses) {
    return courses.stream().anyMatch(Course::isFree); // ⭐ stops at the first match
  }

  public static boolean allPublishedHaveTags(List<Course> courses) {
    return courses.stream().filter(Course::published).allMatch(c -> !c.tags().isEmpty());
  }

  /** count/sum/min/max/average of enrollments in ONE pass. */
  public static IntSummaryStatistics enrollmentStats(List<Course> courses) {
    // ⭐ mapToInt → IntStream: a primitive stream, no boxing to Integer, has sum()/average()/
    //    summaryStatistics().
    return courses.stream()
        .filter(Course::published)
        .mapToInt(Course::enrollments)
        .summaryStatistics();
  }

  /** Cheapest and most expensive published price, computed in a single pass. */
  public record PriceRange(Money min, Money max) {}

  public static PriceRange priceRange(List<Course> courses) {
    // ⭐ teeing (Java 12): feed every element to TWO collectors, then combine their results.
    return courses.stream()
        .filter(Course::published)
        .map(Course::price)
        .collect(
            teeing(
                // ⭐ Comparator.<Money>naturalOrder(): an explicit TYPE WITNESS. Nested inside
                //    teeing, javac can't infer T on its own ("incompatible upper bounds"), so we
                //    name it. Money implements Comparable (note 01), so natural order works.
                minBy(Comparator.<Money>naturalOrder()),
                maxBy(Comparator.<Money>naturalOrder()),
                (min, max) -> new PriceRange(min.orElseThrow(), max.orElseThrow())));
  }

  // ======================================================================= newer & advanced

  /** Sales in fixed-size batches, e.g. to send to an accounting API 3 at a time. */
  public static List<List<Sale>> inBatches(List<Sale> sales, int batchSize) {
    // ⭐ Stream gatherers (Java 24): custom intermediate operations. windowFixed groups
    //    consecutive elements into lists of `batchSize` (the last may be shorter).
    return sales.stream().gather(Gatherers.windowFixed(batchSize)).toList();
  }

  /**
   * Demonstrates LAZINESS: records every course the filter looks at. Only as many elements as
   * needed are processed — findFirst stops the pipeline early.
   *
   * <p>(Side effects inside a stream are normally a mistake; this one exists only to make the
   * evaluation order visible in a test.)
   */
  public static Optional<String> firstPremiumTitle(List<Course> courses, List<String> visited) {
    Money premium = Money.of(1_900_00, "INR");
    return courses.stream()
        .filter(
            c -> {
              visited.add(c.id());
              return c.price().compareTo(premium) >= 0;
            })
        .map(Course::title)
        .findFirst();
  }
}
