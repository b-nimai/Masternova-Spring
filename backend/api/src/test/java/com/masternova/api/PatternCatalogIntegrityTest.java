package com.masternova.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.PatternCatalog;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Keeps patterns/README.md honest: every api class it names must exist and carry
 * {@code @DesignPattern}. (The worker checks its own classes; the kernel checks the links.)
 */
class PatternCatalogIntegrityTest {

  @Test
  void everyCatalogedApiClassExistsAndIsAnnotated() throws ClassNotFoundException {
    PatternCatalog catalog = PatternCatalog.locateFrom(Path.of(""));

    for (String className : catalog.realClassesUnder("com.masternova.api.")) {
      Class<?> type = Class.forName(className);
      assertThat(type.getAnnotationsByType(DesignPattern.class))
          .as("%s is in the catalog but has no @DesignPattern", className)
          .isNotEmpty();
    }
  }
}
