package com.masternova.patterns.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The <b>Factory Method</b> side of the family: a registry of CREATORS ({@code Supplier}s) rather
 * than of ready-made instances. Each call to {@link #create} returns a NEW object — use this when
 * the product holds per-use state (a buffer, a builder, a session) and can't be shared.
 *
 * <p>Classic GoF Factory Method puts {@code createExporter()} as an abstract method that subclasses
 * override; in modern Java a map of {@code Supplier}s (method references to constructors) gives the
 * same "decide which class to instantiate by a key" without a class hierarchy of creators.
 */
public final class ExporterFactory {

  /** Product: stateful, so never shared. */
  public interface Exporter {
    void add(String row);

    String result();
  }

  static final class CsvExporter implements Exporter {
    private final StringBuilder out = new StringBuilder();

    @Override
    public void add(String row) {
      out.append(row).append('\n');
    }

    @Override
    public String result() {
      return out.toString();
    }
  }

  static final class JsonLinesExporter implements Exporter {
    private final StringBuilder out = new StringBuilder();

    @Override
    public void add(String row) {
      out.append("{\"row\":\"").append(row).append("\"}\n");
    }

    @Override
    public String result() {
      return out.toString();
    }
  }

  // ⭐ constructor references ARE factory methods: CsvExporter::new is a Supplier<Exporter>
  private static final Map<String, Supplier<Exporter>> CREATORS =
      Map.of("csv", CsvExporter::new, "jsonl", JsonLinesExporter::new);

  private ExporterFactory() {}

  public static Exporter create(String format) {
    Supplier<Exporter> creator = CREATORS.get(format);
    if (creator == null) {
      throw new IllegalArgumentException("unknown format " + format + "; known: " + formats());
    }
    return creator.get();
  }

  public static List<String> formats() {
    return CREATORS.keySet().stream().sorted().toList();
  }
}
