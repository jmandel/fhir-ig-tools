package org.hl7.fhir.jekyll.test.kramdown;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hl7.fhir.jekyll.kramdown.Kramdown;
import org.hl7.fhir.jekyll.util.Json;

/** Converts all cases sequentially, then concurrently (shuffled, N threads), and compares. */
public final class KramdownConcurrencyCheck {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    List<Map<String, Object>> cases = new ArrayList<>();
    for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        cases.add((Map<String, Object>) Json.parse(line));
      }
    }
    int threads = args.length > 1 ? Integer.parseInt(args[1]) : 8;
    List<String> seq = new ArrayList<>();
    for (Map<String, Object> c : cases) {
      seq.add(conv(c));
    }
    ExecutorService ex = Executors.newFixedThreadPool(threads);
    List<Integer> order = new ArrayList<>();
    for (int r = 0; r < 3; r++) {
      for (int i = 0; i < cases.size(); i++) {
        order.add(i);
      }
    }
    java.util.Collections.shuffle(order, new java.util.Random(42));
    List<Future<String>> fs = new ArrayList<>();
    for (int i : order) {
      fs.add(ex.submit(() -> conv(cases.get(i))));
    }
    int bad = 0;
    for (int k = 0; k < order.size(); k++) {
      if (!fs.get(k).get().equals(seq.get(order.get(k)))) {
        bad++;
      }
    }
    ex.shutdown();
    System.out.println("concurrent conversions=" + order.size() + " threads=" + threads + " mismatches=" + bad);
  }

  @SuppressWarnings("unchecked")
  static String conv(Map<String, Object> c) {
    try {
      return Kramdown.toHtml((String) c.get("text"), (Map<String, Object>) c.get("config"));
    } catch (RuntimeException e) {
      return "ERROR " + e;
    }
  }
}
