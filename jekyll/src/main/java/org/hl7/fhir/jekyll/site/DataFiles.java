package org.hl7.fhir.jekyll.site;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hl7.fhir.jekyll.util.Json;

/**
 * Port of jekyll-4.4.1/lib/jekyll/readers/data_reader.rb: builds `site.data` from `_data`.
 * Entry order matches Ruby's `Dir["*.{yaml,yml,json,csv,tsv}"] + dirs`: brace alternatives are
 * expanded in order, each group sorted; then subdirectories (sorted). JSON files go through
 * SafeYAML in Jekyll; YAML is a JSON superset, so a JSON parser gives the same data.
 */
public final class DataFiles {
  private static final String[] EXTS = {"yaml", "yml", "json", "csv", "tsv"};
  private static final Pattern SANITIZE_1 = Pattern.compile("[^\\w\\s-]+|(?<=^|\\b\\s)\\s+(?=$|\\s?\\b)", Pattern.UNIX_LINES);
  private static final Pattern SANITIZE_2 = Pattern.compile("\\s+", Pattern.UNIX_LINES);

  private DataFiles() {}

  public static Map<String, Object> read(Path dataDir) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (Files.isDirectory(dataDir)) {
      readInto(dataDir, out);
    }
    return out;
  }

  static String sanitizeFilename(String name) {
    return SANITIZE_2.matcher(SANITIZE_1.matcher(name).replaceAll("")).replaceAll("_");
  }

  private static void readInto(Path dir, Map<String, Object> data) {
    List<Path> children;
    try (Stream<Path> s = Files.list(dir)) {
      children = new ArrayList<>(s.toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    children.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
    List<Path> entries = new ArrayList<>();
    for (String ext : EXTS) {
      for (Path p : children) {
        String n = p.getFileName().toString();
        // Ruby Dir glob "*" skips dotfiles; extension match is case-sensitive in the glob
        if (!n.startsWith(".") && n.endsWith("." + ext) && Files.isRegularFile(p)) {
          entries.add(p);
        }
      }
    }
    for (Path p : children) {
      if (!p.getFileName().toString().startsWith(".") && Files.isDirectory(p)) {
        entries.add(p);
      }
    }
    // parse files in parallel (large JSON files dominate), then insert in Jekyll's key order
    Object[] values = new Object[entries.size()];
    List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      final int k = i;
      if (!Files.isDirectory(entries.get(k))) {
        jobs.add(() -> {
          values[k] = readFile(entries.get(k));
          return null;
        });
      }
    }
    try {
      Site.runParallel(jobs, Site.ioThreads());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (int i = 0; i < entries.size(); i++) {
      Path p = entries.get(i);
      String n = p.getFileName().toString();
      if (Files.isDirectory(p)) {
        Map<String, Object> sub = new LinkedHashMap<>();
        data.put(sanitizeFilename(n), sub);
        readInto(p, sub);
      } else {
        String base = n.lastIndexOf('.') > 0 ? n.substring(0, n.lastIndexOf('.')) : n;
        data.put(sanitizeFilename(base), values[i]);
      }
    }
  }

  public static Object readFile(Path p) {
    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
    String text;
    try {
      text = Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (n.endsWith(".csv") || n.endsWith(".tsv")) {
      return Csv.readWithHeaders(text, n.endsWith(".tsv") ? '\t' : ',');
    }
    return org.hl7.fhir.jekyll.yaml.Yaml.loadFile(p); // SafeYAML.load_file, also for .json (as Jekyll)
  }

  public static Object parseYaml(String text) {
    return org.hl7.fhir.jekyll.yaml.Yaml.load(text);
  }
}
