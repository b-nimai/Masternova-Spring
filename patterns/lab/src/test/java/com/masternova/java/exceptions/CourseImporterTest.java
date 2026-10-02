package com.masternova.java.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.exceptions.CourseImporter.ImportReport;
import com.masternova.java.exceptions.CourseImporter.LineError;
import com.masternova.java.valueobject.Money;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CourseImporterTest {

  @TempDir Path dir; // JUnit creates a fresh directory per test and deletes it afterwards

  @Test
  void goodLinesAreImportedAndBadLinesAreReportedNotThrown() throws IOException {
    Path csv = dir.resolve("courses.csv");
    Files.writeString(
        csv,
        """
        id,title,priceRupees
        c1,Spring Boot Fundamentals,1499
        c2,Java Streams,abc
        c3,Angular Signals,1299.50

        c4,,100
        c5,Too,Many,Columns
        """);

    ImportReport report = CourseImporter.importFrom(csv);

    assertThat(report.imported()).extracting(CourseImporter.ImportedCourse::id).containsExactly("c1", "c3");
    assertThat(report.imported().get(1).price()).isEqualTo(Money.of(1_299_50, "INR"));
    assertThat(report.errors()).extracting(LineError::lineNumber).containsExactly(3, 6, 7);
  }

  @Test
  void anUnreadableFileIsACheckedException() {
    Path missing = dir.resolve("nope.csv");

    // a CHECKED exception: the test method had to declare or handle it — the compiler insisted
    assertThatThrownBy(() -> CourseImporter.importFrom(missing))
        .isInstanceOf(IOException.class)
        .isInstanceOf(NoSuchFileException.class);
  }

  @Test
  void theUncheckedVariantWrapsAndKeepsTheCause() {
    Path missing = dir.resolve("nope.csv");

    assertThatThrownBy(() -> CourseImporter.importFromUnchecked(missing))
        .isInstanceOf(UncheckedIOException.class)
        .hasCauseInstanceOf(NoSuchFileException.class);
  }
}
