package com.masternova.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fails the build when a migration would break the version that is still running.
 *
 * <p>⭐ On Kubernetes the migration runs BEFORE the rollout (the db-migrate PreSync hook), and the
 * old pods keep serving until the rollout ends. So every migration must work with the previous
 * release's code. Dropping, renaming or retyping a column breaks it: measured in the D4.8 drill
 * (devops/06 §8). Such a change takes three releases (expand → switch → contract), and only the
 * last one may contain the destructive statement. It says so in its first line:
 *
 * <pre>-- contract: V22 (expand) — every running release reads course.tagline since then</pre>
 *
 * The expand migration it names must exist and be older. No Spring, no database: the rule is text.
 */
class MigrationSafetyTest {

  /** DDL that breaks the code of the release before it. */
  private static final Pattern DESTRUCTIVE =
      Pattern.compile(
          "\\bDROP\\s+(TABLE|COLUMN)\\b"
              + "|\\bRENAME\\s+(COLUMN\\s+\\w+\\s+)?TO\\b"
              + "|\\bALTER\\s+COLUMN\\s+\\w+\\s+(SET\\s+DATA\\s+)?TYPE\\b"
              + "|\\bALTER\\s+COLUMN\\s+\\w+\\s+SET\\s+NOT\\s+NULL\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern CONTRACT = Pattern.compile("^--\\s*contract:\\s*V(\\d+)\\b");
  private static final Pattern VERSION = Pattern.compile("^V(\\d+)__\\w+\\.sql$");

  @Test
  void everyMigrationKeepsThePreviousReleaseWorking() throws IOException, URISyntaxException {
    Map<Integer, String> migrations = migrations();
    assertThat(migrations)
        .as("the api's and the messaging module's migrations")
        .containsKeys(1, 2, 16, 21);

    List<String> problems = new ArrayList<>();
    migrations.forEach((version, sql) -> problems.addAll(check(version, sql, migrations)));

    assertThat(problems).as("migrations that break the release still running").isEmpty();
  }

  @Test
  void additiveChangesPass() {
    assertThat(check(30, "ALTER TABLE course ADD COLUMN tagline VARCHAR(200);", Map.of()))
        .isEmpty();
    assertThat(check(30, "CREATE INDEX course_tagline_idx ON course (tagline);", Map.of()))
        .isEmpty();
    assertThat(check(30, "ALTER TABLE course ALTER COLUMN tagline DROP NOT NULL;", Map.of()))
        .as("relaxing a constraint never breaks the old writer")
        .isEmpty();
  }

  @Test
  void destructiveChangesFailWithoutAContractLine() {
    assertThat(check(30, "ALTER TABLE course RENAME COLUMN subtitle TO tagline;", Map.of()))
        .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
        .contains("V30", "RENAME COLUMN subtitle TO");
    assertThat(check(30, "alter table course drop column subtitle;", Map.of())).hasSize(1);
    assertThat(check(30, "ALTER TABLE course RENAME TO courses;", Map.of())).hasSize(1);
    assertThat(check(30, "ALTER TABLE course ALTER COLUMN level TYPE text;", Map.of())).hasSize(1);
    assertThat(check(30, "ALTER TABLE course ALTER COLUMN title SET NOT NULL;", Map.of()))
        .hasSize(1);
    assertThat(check(30, "DROP TABLE course_old;", Map.of())).hasSize(1);
  }

  @Test
  void aContractNamesAnOlderExpandThatExists() {
    String contract =
        """
        -- contract: V22 (expand) — every running release reads course.tagline since then
        ALTER TABLE course DROP COLUMN subtitle;
        """;
    Map<Integer, String> withExpand = Map.of(22, "ALTER TABLE course ADD COLUMN tagline TEXT;");

    assertThat(check(23, contract, withExpand)).isEmpty();
    assertThat(check(23, contract, Map.of())).singleElement().asString().contains("V22");
    assertThat(check(21, contract, withExpand)).as("an expand from the future").hasSize(1);
  }

  @Test
  void commentsAreNotStatements() {
    assertThat(check(30, "-- later: DROP COLUMN subtitle\nSELECT 1;", Map.of())).isEmpty();
    assertThat(check(30, "/* RENAME COLUMN a TO b */ SELECT 1;", Map.of())).isEmpty();
  }

  static List<String> check(int version, String sql, Map<Integer, String> all) {
    String firstLine = sql.strip().lines().findFirst().orElse("");
    Matcher contract = CONTRACT.matcher(firstLine);
    Matcher destructive = DESTRUCTIVE.matcher(withoutComments(sql));
    if (!destructive.find()) {
      return List.of();
    }
    if (!contract.find()) {
      return List.of(
          "V%d: '%s' breaks the release still running during the rollout. Expand first; this"
                  .formatted(version, destructive.group().replaceAll("\\s+", " "))
              + " belongs in a later contract migration whose first line is '-- contract: V<n>'");
    }
    int expand = Integer.parseInt(contract.group(1));
    if (expand >= version || !all.containsKey(expand)) {
      return List.of(
          "V%d: its contract line names V%d, which must be an older migration that exists"
              .formatted(version, expand));
    }
    return List.of();
  }

  private static String withoutComments(String sql) {
    return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ");
  }

  /**
   * Every {@code db/migration} on the classpath, as Flyway sees them: the api's own, the test-only
   * ones and the messaging module's (V2, V16: the outbox), which arrive in its jar. ⭐ Not {@code
   * getResource}: the test classpath's own {@code db/migration} comes FIRST and hid the real ones —
   * the first version of this test checked one file and passed with the drill's rename in place.
   */
  private static Map<Integer, String> migrations() throws IOException, URISyntaxException {
    Map<Integer, String> byVersion = new TreeMap<>();
    for (URL url : Collections.list(classLoader().getResources("db/migration"))) {
      URI uri = url.toURI();
      if ("jar".equals(uri.getScheme())) {
        try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
          readInto(byVersion, jar.getPath("db/migration"));
        }
      } else {
        readInto(byVersion, Path.of(uri));
      }
    }
    return byVersion;
  }

  private static void readInto(Map<Integer, String> byVersion, Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : files.toList()) {
        Matcher name = VERSION.matcher(file.getFileName().toString());
        assertThat(name.matches()).as("Flyway file name: %s", file.getFileName()).isTrue();
        byVersion.put(Integer.parseInt(name.group(1)), Files.readString(file));
      }
    }
  }

  private static ClassLoader classLoader() {
    return MigrationSafetyTest.class.getClassLoader();
  }
}
