package com.masternova.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

  @Test
  void aTitleBecomesAUrlSafeSlugWithARandomSuffix() {
    assertThat(Slugs.forTitle("Kubernetes: From Zero!"))
        .matches("kubernetes-from-zero-[a-z0-9]{6}");
    assertThat(Slugs.forTitle("Café Déjà Vu")).matches("cafe-deja-vu-[a-z0-9]{6}");
    assertThat(Slugs.forTitle("!!!")).matches("course-[a-z0-9]{6}");
    assertThat(Slugs.forTitle("x".repeat(300))).hasSizeLessThanOrEqualTo(140);
  }

  @Test
  void twoCoursesWithOneTitleGetDifferentSlugs() {
    assertThat(Slugs.forTitle("Docker")).isNotEqualTo(Slugs.forTitle("Docker"));
  }

  @Test
  void aCopyOfACopyDoesNotStackSuffixes() {
    String copy = Slugs.forCopyOf("k8s");
    assertThat(copy).matches("k8s-copy-[a-z0-9]{6}");
    assertThat(Slugs.forCopyOf(copy)).matches("k8s-copy-[a-z0-9]{6}");
  }
}
