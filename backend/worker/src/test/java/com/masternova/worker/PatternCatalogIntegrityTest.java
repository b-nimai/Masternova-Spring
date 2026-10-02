package com.masternova.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.PatternCatalog;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Every worker class named in patterns/README.md must exist and carry {@code @DesignPattern}. */
class PatternCatalogIntegrityTest {

  @Test
  void everyCatalogedWorkerClassExistsAndIsAnnotated() throws ClassNotFoundException {
    PatternCatalog catalog = PatternCatalog.locateFrom(Path.of(""));

    for (String className : catalog.realClassesUnder("com.masternova.worker.")) {
      Class<?> type = Class.forName(className);
      assertThat(type.getAnnotationsByType(DesignPattern.class))
          .as("%s is in the catalog but has no @DesignPattern", className)
          .isNotEmpty();
    }
  }
}
