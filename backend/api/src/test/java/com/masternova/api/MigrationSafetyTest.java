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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * release's code: reads must still find what they read, writes must still be accepted. Dropping,
 * renaming or retyping something it uses breaks the reads; a new NOT NULL column without a default,
 * a new constraint or a stricter type breaks the writes. Measured in the D4.8 drill (devops/06 §8):
 * a one-release rename failed 70 % of requests during its rollout. Such a change takes three
 * releases (expand → switch → contract), and only the last one may contain the breaking statement.
 * It says so in its first line:
 *
 * <pre>-- contract: V22 (expand) — every running release reads course.tagline since then</pre>
 *
 * The expand migration it names must exist and be older. No Spring, no database: the rule is text,
 * checked statement by statement.
 */
class MigrationSafetyTest {

  /**
   * V1–V21 shipped before deployments rolled out under load (D4): each api pod migrated at its own
   * start, against no older running version. V9's stricter currency CHECK would fail the rule
   * today.
   */
  private static final int ROLLING_DEPLOYS_SINCE = 22;

  private static final String ID = "(?:\"[^\"]+\"|\\w+)"; // plain or "quoted"
  private static final String ALTER_TABLE = "^ALTER TABLE (?:IF EXISTS )?(?:ONLY )?" + ID + " ";

  /** What breaks the release before it, matched against ONE statement (upper case, one space). */
  private static final Map<String, Pattern> BREAKING = new LinkedHashMap<>();

  static {
    rule(
        "drops a table, view, type, function or sequence",
        "^DROP (?:TABLE|VIEW|MATERIALIZED VIEW|TYPE|FUNCTION|SEQUENCE)\\b");
    rule("empties a table", "^TRUNCATE\\b");
    rule("renames a table or column", ALTER_TABLE + ".*\\bRENAME (?!CONSTRAINT\\b)");
    rule(
        "drops a column",
        ALTER_TABLE
            + ".*\\bDROP (?!CONSTRAINT\\b|DEFAULT\\b|NOT NULL\\b|IDENTITY\\b|EXPRESSION\\b)"
            + "(?:COLUMN )?(?:IF EXISTS )?"
            + ID);
    rule(
        "changes a column's type",
        ALTER_TABLE + ".*\\bALTER (?:COLUMN )?" + ID + " (?:SET DATA )?TYPE\\b");
    rule("makes a column NOT NULL", ALTER_TABLE + ".*\\bSET NOT NULL\\b");
    rule(
        "adds a constraint the old writer may violate",
        ALTER_TABLE
            + ".*\\bADD (?:CONSTRAINT "
            + ID
            + " )?(?:CHECK|UNIQUE|FOREIGN KEY|PRIMARY KEY|EXCLUDE)\\b");
  }

  /** An ADD clause that adds a column (not a constraint). */
  private static final Pattern ADD_COLUMN =
      Pattern.compile(
          "^(?:"
              + ALTER_TABLE
              + ")?ADD (?:COLUMN )?(?!CONSTRAINT\\b|CHECK\\b|UNIQUE\\b|FOREIGN\\b|PRIMARY\\b|EXCLUDE\\b)");

  private static void rule(String what, String regex) {
    BREAKING.put(what, Pattern.compile(regex));
  }

  private static final Pattern CONTRACT = Pattern.compile("^--\\s*contract:\\s*V(\\d+)\\b");
  private static final Pattern VERSION = Pattern.compile("^V(\\d+)__\\w+\\.sql$");

  @Test
  void everyMigrationKeepsThePreviousReleaseWorking() throws IOException, URISyntaxException {
    Map<Integer, String> migrations = migrations();
    assertThat(migrations)
        .as("the api's and the messaging module's migrations")
        .containsKeys(1, 2, 16, 21);

    List<String> problems = new ArrayList<>();
    migrations.forEach(
        (version, sql) -> {
          if (version >= ROLLING_DEPLOYS_SINCE) {
            problems.addAll(check(version, sql, migrations));
          }
        });

    assertThat(problems).as("migrations that break the release still running").isEmpty();
  }

  @Test
  void additiveChangesPass() {
    for (String sql :
        List.of(
            "ALTER TABLE course ADD COLUMN tagline VARCHAR(200);",
            "ALTER TABLE course ADD COLUMN level_rank INT NOT NULL DEFAULT 0;",
            "ALTER TABLE course ADD COLUMN IF NOT EXISTS rank INT DEFAULT 0 NOT NULL;",
            "CREATE TABLE review (id UUID PRIMARY KEY, body TEXT NOT NULL);",
            "CREATE INDEX course_tagline_idx ON course (tagline);",
            "ALTER INDEX course_tagline_idx RENAME TO course_tagline_ix;",
            "ALTER TABLE course ALTER COLUMN tagline DROP NOT NULL;",
            "ALTER TABLE course DROP CONSTRAINT course_currency_ck;",
            "ALTER TABLE course ALTER COLUMN tagline DROP DEFAULT;")) {
      assertThat(check(30, sql, Map.of())).as(sql).isEmpty();
    }
  }

  @Test
  void breakingChangesFailWithoutAContractLine() {
    for (String sql :
        List.of(
            "ALTER TABLE course RENAME COLUMN subtitle TO tagline;",
            "ALTER TABLE course RENAME subtitle TO tagline;",
            "ALTER TABLE course RENAME TO courses;",
            "alter table course drop column subtitle;",
            "ALTER TABLE course DROP subtitle;",
            "ALTER TABLE course DROP COLUMN IF EXISTS \"subtitle\";",
            "ALTER TABLE course ALTER COLUMN level TYPE text;",
            "ALTER TABLE course ALTER \"level\" SET DATA TYPE text;",
            "ALTER TABLE course ALTER COLUMN title SET NOT NULL;",
            "ALTER TABLE course ADD COLUMN tagline TEXT NOT NULL;",
            "ALTER TABLE course ADD tagline TEXT NOT NULL, ADD rank INT DEFAULT 0;",
            "ALTER TABLE course ADD CONSTRAINT course_currency_ck CHECK (currency = 'INR');",
            "ALTER TABLE course ADD UNIQUE (slug);",
            "DROP TABLE course_old;",
            "DROP VIEW course_stats;",
            "DROP FUNCTION course_sync_tagline();",
            "TRUNCATE course_old;")) {
      assertThat(check(30, sql, Map.of())).as(sql).singleElement().asString().contains("V30");
    }
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
    assertThat(check(30, "/* ALTER TABLE a RENAME b TO c; */ SELECT 1;", Map.of())).isEmpty();
  }

  static List<String> check(int version, String sql, Map<Integer, String> all) {
    List<String> breaking = new ArrayList<>();
    for (String statement : withoutComments(sql).split(";")) {
      String normal = statement.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
      BREAKING.forEach(
          (what, rule) -> {
            if (rule.matcher(normal).find()) {
              breaking.add("'%s' %s".formatted(normal, what));
            }
          });
      // ⭐ in code, not one regex: nested "anything but DEFAULT" quantifiers backtracked forever
      if (normal.startsWith("ALTER TABLE ")) {
        for (String clause : normal.split(",")) {
          String c = clause.strip();
          if (ADD_COLUMN.matcher(c).find() && c.contains(" NOT NULL") && !c.contains(" DEFAULT ")) {
            breaking.add("'%s' adds a NOT NULL column without a default".formatted(normal));
          }
        }
      }
    }
    if (breaking.isEmpty()) {
      return List.of();
    }
    Matcher contract = CONTRACT.matcher(sql.strip().lines().findFirst().orElse(""));
    if (!contract.find()) {
      return List.of(
          "V%d: %s — breaks the release still running during the rollout. Expand first; this"
                  .formatted(version, String.join("; ", breaking))
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
    for (URL url :
        Collections.list(MigrationSafetyTest.class.getClassLoader().getResources("db/migration"))) {
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
}
