package com.masternova.java.generics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.sealed.ArticleContent;
import com.masternova.java.sealed.LectureContent;
import com.masternova.java.sealed.VideoContent;
import com.masternova.java.valueobject.LectureDuration;
import com.masternova.java.valueobject.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class RankingTest {

  @Test
  void maxWorksForAnyComparableType() {
    assertThat(Ranking.max(List.of(3, 9, 4))).isEqualTo(9);
    assertThat(Ranking.max(List.of(Money.of(5, "INR"), Money.of(50, "INR"))))
        .isEqualTo(Money.of(50, "INR"));
  }

  @Test
  void superBoundIsWhatMakesLocalDateWork() {
    // LocalDate implements Comparable<ChronoLocalDate> — a SUPERTYPE of LocalDate.
    // With a naive <T extends Comparable<T>> bound, max(dates) fails to compile:
    //   "inference variable T has incompatible equality constraints ChronoLocalDate,LocalDate"
    // The Comparable<? super T> bound accepts it.
    List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 6, 1));
    LocalDate latest = Ranking.max(dates);

    assertThat(latest).isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void maxOfNothingIsAnError() {
    assertThatThrownBy(() -> Ranking.max(List.<Integer>of()))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void copyAllFromProducerIntoConsumer() {
    List<VideoContent> videos = List.of(new VideoContent("Intro", LectureDuration.ofMinutes(5)));
    List<LectureContent> lectures = new ArrayList<>();
    List<Object> everything = new ArrayList<>();

    Ranking.copyAll(videos, lectures); //   T = VideoContent: ? extends T ← videos, ? super T ← lectures
    Ranking.copyAll(videos, everything); // ...and Object is a supertype too

    assertThat(lectures).hasSize(1);
    assertThat(everything).hasSize(1);
  }

  @Test
  void extendsWildcardAcceptsListsOfAnySubtype() {
    List<VideoContent> videos = List.of(new VideoContent("Intro", LectureDuration.parse("4:05")));
    List<ArticleContent> articles = List.of(new ArticleContent("Notes", 1_000));

    assertThat(Ranking.totalMinutes(videos)).isEqualTo(5);
    assertThat(Ranking.totalMinutes(articles)).isEqualTo(5);
  }

  @Test
  void arraysAreCovariantAndFailOnlyAtRuntime() {
    // ⭐ Arrays ARE covariant: a String[] may be used as an Object[] ...
    Object[] objects = new String[1];
    // ... so this compiles, and blows up at RUNTIME. Generics are invariant precisely so this
    //     mistake is caught at COMPILE time instead (List<Object> objs = new ArrayList<String>()
    //     does not compile).
    assertThatThrownBy(() -> objects[0] = 42).isInstanceOf(ArrayStoreException.class);
  }
}
