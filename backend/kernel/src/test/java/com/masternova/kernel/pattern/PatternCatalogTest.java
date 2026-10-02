package com.masternova.kernel.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatternCatalogTest {

  @Test
  void parsesOnlyDataRowsBetweenTheMarkers(@TempDir Path dir) throws IOException {
    Path readme = dir.resolve("README.md");
    Files.writeString(
        readme,
        """
        | 9 | Ignored | outside the markers | | `com.masternova.api.Nope` | | | |
        <!-- catalog:start -->
        | # | Pattern | Type | Where | Real class | Note | Lab | Status |
        |---|---------|------|-------|------------|------|-----|--------|
        | 1 | Strategy | Behavioral | payments | `com.masternova.api.A`<br>`com.masternova.worker.B` | [note](docs/01-strategy.md) | [lab](lab/src/x/) | ✅ |
        | 2 | State | Behavioral | orders | — | [note](docs/02-state.md#roles) | — | ☐ |
        <!-- catalog:end -->
        """);

    PatternCatalog catalog = PatternCatalog.parse(readme);

    assertThat(catalog.entries())
        .extracting(PatternCatalog.Entry::pattern)
        .containsExactly("Strategy", "State");
    assertThat(catalog.realClassesUnder("com.masternova.api."))
        .containsExactly("com.masternova.api.A");
    assertThat(catalog.realClassesUnder("com.masternova.worker."))
        .containsExactly("com.masternova.worker.B");
    assertThat(catalog.entries().get(1).links()).containsExactly("docs/02-state.md");
  }

  /** The real catalog: every note and lab link must point at something that exists. */
  @Test
  void everyLinkInTheRealCatalogExists() {
    PatternCatalog catalog = PatternCatalog.locateFrom(Path.of(""));

    assertThat(catalog.entries()).isNotEmpty();
    for (PatternCatalog.Entry entry : catalog.entries()) {
      for (String link : entry.links()) {
        assertThat(catalog.patternsDir().resolve(link))
            .as("%s links to %s", entry.pattern(), link)
            .exists();
      }
    }
  }
}
