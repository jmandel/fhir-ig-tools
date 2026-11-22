package org.hl7.fhir.jekyll.test.kramdown;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.kramdown.Kramdown;
import org.hl7.fhir.jekyll.util.Json;

/** Times converting every case of a kramdown cases.jsonl once (after warmup rounds). */
public final class KramdownBench {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    List<Map<String, Object>> cases = new ArrayList<>();
    for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        cases.add((Map<String, Object>) Json.parse(line));
      }
    }
    int warm = args.length > 1 ? Integer.parseInt(args[1]) : 5;
    int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
    long chars = 0;
    final Thread main = Thread.currentThread();
    final java.util.Map<String, Integer> prof = new java.util.concurrent.ConcurrentHashMap<>();
    final boolean profile = System.getProperty("prof") != null;
    if (profile) {
      Thread t = new Thread(() -> {
        while (true) {
          StackTraceElement[] st = main.getStackTrace();
          StringBuilder key = new StringBuilder();
          int n = 0;
          for (StackTraceElement e : st) {
            if (e.getClassName().contains("kramdown")) {
              key.append(e.getClassName().replaceAll(".*\\.", "")).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber()).append(" < ");
              if (++n >= Integer.getInteger("depth", 3)) {
                break;
              }
            }
          }
          prof.merge(key.toString(), 1, Integer::sum);
          try {
            Thread.sleep(1);
          } catch (InterruptedException ex) {
            return;
          }
        }
      });
      t.setDaemon(true);
      t.start();
    }
    for (int r = 0; r < warm + rounds; r++) {
      java.lang.management.ThreadMXBean mx = java.lang.management.ManagementFactory.getThreadMXBean();
      long c0 = mx.getCurrentThreadCpuTime();
      long t0 = System.nanoTime();
      for (Map<String, Object> c : cases) {
        try {
          String out = Kramdown.toHtml((String) c.get("text"), (Map<String, Object>) c.get("config"));
          chars += out.length();
        } catch (RuntimeException e) {
          // ignore
        }
      }
      long t1 = System.nanoTime();
      long c1 = mx.getCurrentThreadCpuTime();
      System.out.printf("%s round %d: %d cases in %.1f ms wall, %.1f ms cpu%n", r < warm ? "warmup" : "timed", r,
          cases.size(), (t1 - t0) / 1e6, (c1 - c0) / 1e6);
    }
    System.out.println("chars=" + chars);
    if (profile) {
      prof.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40)
          .forEach(e -> System.out.println(e.getValue() + "  " + e.getKey()));
    }
  }
}
