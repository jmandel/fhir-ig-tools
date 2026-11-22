package org.hl7.fhir.jekyll.test.yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.util.Json;
import org.hl7.fhir.jekyll.yaml.Yaml;

/**
 * Times Yaml.loadFile over the data-file cases (and Json.parseAsYaml on the .json ones, for comparison).
 * usage: YamlBench jekyll/tests/site/data-cases.jsonl [rounds]
 */
public final class YamlBench {
  private YamlBench() {}

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    List<Path> paths = new ArrayList<>();
    long bytes = 0;
    for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        Path p = Path.of((String) ((Map<String, Object>) Json.parse(line)).get("host_path"));
        paths.add(p);
        bytes += Files.size(p);
      }
    }
    int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 5;
    for (int r = 0; r < rounds; r++) {
      long t0 = System.nanoTime();
      for (Path p : paths) {
        Yaml.loadFile(p);
      }
      long t1 = System.nanoTime();
      for (Path p : paths) {
        if (p.toString().endsWith(".json")) {
          String text = Files.readString(p, StandardCharsets.UTF_8);
          if (!text.isBlank()) {
            Json.parseAsYaml(text);
          }
        }
      }
      long t2 = System.nanoTime();
      System.out.printf("round %d: %d files, %.1f MB: Yaml.loadFile %.0f ms, Json.parseAsYaml(.json only) %.0f ms%n", r, paths.size(),
          bytes / 1e6, (t1 - t0) / 1e6, (t2 - t1) / 1e6);
    }
  }
}
