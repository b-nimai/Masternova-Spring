package com.masternova.patterns.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.masternova.patterns.facade.Subsystems.Catalog;
import com.masternova.patterns.facade.Subsystems.CourseFacts;
import com.masternova.patterns.facade.Subsystems.Purchases;
import com.masternova.patterns.facade.Subsystems.Rules;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FacadeTest {

  private final CourseFacts k8s = new CourseFacts("k8s", "asha", false, Set.of("intro"));
  private final Purchases purchases = new Purchases(Set.of("ben:k8s"));
  private final AccessFacade access =
      new AccessFacade(
          new Catalog(Map.of("intro", k8s, "pods", k8s)), purchases, new Rules());

  @Test
  void callersAskOneQuestionInsteadOfOrchestratingThreeSubsystems() {
    assertThat(access.mayWatch("ben", "pods")).isTrue(); // bought
    assertThat(access.mayWatch("cara", "intro")).isTrue(); // preview
    assertThat(access.mayWatch("cara", "pods")).isFalse();
    assertThat(access.mayWatch("asha", "pods")).isTrue(); // instructor
  }

  @Test
  void theFacadeFetchesTheRowOncePerQuestion() {
    access.mayWatch("ben", "pods");
    assertThat(purchases.lookups()).isEqualTo(1);
  }

  @Test
  void anUnknownLectureIsTheFacadesErrorToRaise() {
    assertThatIllegalArgumentException().isThrownBy(() -> access.mayWatch("ben", "nope"));
  }
}
