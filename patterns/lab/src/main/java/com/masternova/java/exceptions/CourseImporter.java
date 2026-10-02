package com.masternova.java.exceptions;

import com.masternova.java.valueobject.Money;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports courses from a CSV file ({@code id,title,priceRupees}) — two kinds of failure, handled
 * two different ways:
 *
 * <ul>
 *   <li>⭐ the FILE can't be read → a CHECKED {@link IOException}: the caller must decide (retry,
 *       report, abort). It's declared in the signature so it can't be forgotten.
 *   <li>⭐ a LINE is malformed → not an exception at all: collected into the report, so one bad
 *       row doesn't hide the 999 good ones. Expected problems are DATA, not control flow.
 * </ul>
 */
public final class CourseImporter {

  public record ImportedCourse(String id, String title, Money price) {}

  public record LineError(int lineNumber, String reason) {}

  public record ImportReport(List<ImportedCourse> imported, List<LineError> errors) {
    public ImportReport {
      imported = List.copyOf(imported);
      errors = List.copyOf(errors);
    }
  }

  private CourseImporter() {}

  public static ImportReport importFrom(Path file) throws IOException {
    List<ImportedCourse> imported = new ArrayList<>();
    List<LineError> errors = new ArrayList<>();

    // ⭐ try-with-resources: the reader is closed automatically — on success AND on exception —
    //    in reverse order of opening. No finally block, no leaked file handles.
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (lineNumber == 1 || line.isBlank()) {
          continue; // header row / empty line
        }
        try {
          imported.add(parse(line));
        } catch (IllegalArgumentException badLine) {
          // ⭐ Catch the NARROWEST type you can handle. NumberFormatException is an IAE, and so
          //    are Money's own validation errors — one catch covers both.
          errors.add(new LineError(lineNumber, badLine.getMessage()));
        }
      }
    }
    return new ImportReport(imported, errors);
  }

  /**
   * For code that can't throw checked exceptions — e.g. inside a stream lambda. ⭐ Wrap in {@link
   * UncheckedIOException} (made for exactly this) and KEEP the cause.
   */
  public static ImportReport importFromUnchecked(Path file) {
    try {
      return importFrom(file);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + file, e);
    }
  }

  private static ImportedCourse parse(String line) {
    String[] parts = line.split(",", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException("expected 3 columns, got " + parts.length);
    }
    String id = parts[0].strip();
    String title = parts[1].strip();
    if (id.isEmpty() || title.isEmpty()) {
      throw new IllegalArgumentException("id and title are required");
    }
    return new ImportedCourse(id, title, Money.parse(parts[2].strip(), "INR"));
  }
}
