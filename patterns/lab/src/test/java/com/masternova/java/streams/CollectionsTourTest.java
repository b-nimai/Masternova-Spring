package com.masternova.java.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.valueobject.Money;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectionsTourTest {

  @Test
  void mergeCountsWords() {
    assertThat(CollectionsTour.wordFrequency("Java streams, java records. Streams!"))
        .containsExactly(Map.entry("java", 2), Map.entry("records", 1), Map.entry("streams", 2));
  }

  @Test
  void computeIfAbsentBuildsAMultimap() {
    Map<String, List<String>> byTag = CollectionsTour.coursesByTag(CatalogData.courses());

    assertThat(byTag.get("docker")).containsExactly("c4", "c5");
    assertThat(byTag.get("angular")).containsExactly("c3", "c6");
  }

  @Test
  void removeIfIsTheSafeWayToRemoveWhileIterating() {
    List<Course> courses = new ArrayList<>(CatalogData.courses());

    CollectionsTour.removeDrafts(courses);

    assertThat(courses).hasSize(8).allMatch(Course::published);
  }

  @Test
  void removingInsideForEachUsuallyThrowsConcurrentModificationException() {
    List<String> ids = new ArrayList<>(List.of("c1", "c2", "c3", "c4"));

    assertThatThrownBy(
            () -> {
              for (String id : ids) {
                if (id.equals("c2")) {
                  ids.remove(id); // ❌ structural change during iteration
                }
              }
            })
        .isInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void butRemovingTheSecondToLastElementSilentlySkipsTheLastOne() {
    // ⭐ The fail-fast check is BEST-EFFORT. Remove the second-to-last element and the
    //    iterator's hasNext() sees cursor == size, so the loop just ENDS: no exception, and the
    //    last element is never visited. Same bug, no error: worse. Use removeIf.
    List<String> ids = new ArrayList<>(List.of("c1", "c2", "c3", "c4"));
    List<String> visited = new ArrayList<>();

    for (String id : ids) {
      visited.add(id);
      if (id.equals("c3")) {
        ids.remove(id);
      }
    }

    assertThat(visited).containsExactly("c1", "c2", "c3"); // c4 was never looked at
    assertThat(ids).containsExactly("c1", "c2", "c4");
  }

  @Test
  void immutableListsRejectChanges() {
    List<String> fixed = List.of("a", "b");

    assertThatThrownBy(() -> fixed.add("c")).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> List.of("a", null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void navigableMapFindsThePriceBand() {
    assertThat(CollectionsTour.priceTier(Money.zero("INR"))).isEqualTo("Free");
    assertThat(CollectionsTour.priceTier(Money.of(899_00, "INR"))).isEqualTo("Budget");
    assertThat(CollectionsTour.priceTier(Money.of(1_000_00, "INR"))).isEqualTo("Standard");
    assertThat(CollectionsTour.priceTier(Money.of(2_499_00, "INR"))).isEqualTo("Premium");
  }

  @Test
  void sequencedCollectionFirstAndLast() {
    assertThat(CollectionsTour.newestAndOldest(CatalogData.courses()))
        .containsExactly("CSS Grid in a Day", "Docker in Practice");
  }
}
