package com.masternova.kernel.pattern;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Reads the catalog table in {@code patterns/README.md} so tests can prove it is not stale.
 *
 * <p>Only the rows between {@code <!-- catalog:start -->} and {@code <!-- catalog:end -->} are
 * parsed. Columns: {@code # | Pattern | Type | Where in Masternova | Real class | Note | Lab |
 * Status}.
 */
public final class PatternCatalog {

  private static final String START = "<!-- catalog:start -->";
  private static final String END = "<!-- catalog:end -->";
  private static final java.util.regex.Pattern FQCN =
      java.util.regex.Pattern.compile("`(com\\.masternova\\.[\\w.]+)`");
  private static final java.util.regex.Pattern LINK =
      java.util.regex.Pattern.compile("\\]\\(([^)#]+)[^)]*\\)");

  /** One catalog row: the classes it names in real code and the files it links to. */
  public record Entry(String pattern, List<String> realClasses, List<String> links) {}

  private final Path patternsDir;
  private final List<Entry> entries;

  private PatternCatalog(Path patternsDir, List<Entry> entries) {
    this.patternsDir = patternsDir;
    this.entries = List.copyOf(entries);
  }

  /** Walks up from {@code start} until it finds {@code patterns/README.md}. */
  public static PatternCatalog locateFrom(Path start) {
    for (Path dir = start.toAbsolutePath(); dir != null; dir = dir.getParent()) {
      Path readme = dir.resolve("patterns").resolve("README.md");
      if (Files.isRegularFile(readme)) {
        return parse(readme);
      }
    }
    throw new IllegalStateException("patterns/README.md not found above " + start);
  }

  static PatternCatalog parse(Path readme) {
    String text;
    try {
      text = Files.readString(readme);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    int from = text.indexOf(START);
    int to = text.indexOf(END);
    if (from < 0 || to < from) {
      throw new IllegalStateException("catalog markers missing in " + readme);
    }

    List<Entry> entries = new ArrayList<>();
    for (String line : text.substring(from + START.length(), to).split("\\R")) {
      String[] cells = line.split("\\|");
      // a data row: | # | Pattern | ... — skip the header and the |---| separator
      if (cells.length < 9 || !cells[1].strip().matches("\\d+")) {
        continue;
      }
      entries.add(
          new Entry(cells[2].strip(), all(FQCN.matcher(cells[5])), all(LINK.matcher(line))));
    }
    return new PatternCatalog(readme.getParent(), entries);
  }

  private static List<String> all(Matcher m) {
    List<String> out = new ArrayList<>();
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }

  public List<Entry> entries() {
    return entries;
  }

  /** The {@code patterns/} directory; catalog links are relative to it. */
  public Path patternsDir() {
    return patternsDir;
  }

  /** Real classes whose name starts with {@code prefix} — each deployable checks its own. */
  public List<String> realClassesUnder(String prefix) {
    return entries.stream()
        .flatMap(e -> e.realClasses().stream())
        .filter(c -> c.startsWith(prefix))
        .toList();
  }
}
