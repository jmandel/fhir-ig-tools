package org.hl7.fhir.jekyll.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hl7.fhir.jekyll.kramdown.Kramdown;

/** P0.12: the Java kramdown port on the same pathological long-line shapes as jekyll/oracle/longline_bench.rb. */
public final class LongLineJava {
  public static void main(String[] args) throws Exception {
    Map<String, String> cases = new LinkedHashMap<>();
    cases.put("\"_b \" x100", "_b ".repeat(100));
    cases.put("\"_b \" x1000", "_b ".repeat(1000));
    cases.put("\"*a \" x1000", "*a ".repeat(1000));
    cases.put("snake_case ids x1000", "some_identifier_name ".repeat(1000));
    cases.put("_leading words x1000", "_word ".repeat(1000));
    cases.put("pipe row 40k", "| a | b |\n|---|---|\n|" + " x |".repeat(10000));
    cases.put("emphasis chars 40k", "*a* _b* ".repeat(5000));
    cases.put("td cells 80k", "<table><tr>" + "<td>x</td>".repeat(8000) + "</tr></table>");
    Kramdown.toHtml("warm *up* `x` _y_", null);
    ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(null, r, "bench", 512L << 20); // deep recursion headroom, like Ruby's VM stack
      t.setDaemon(true);
      return t;
    });
    for (Map.Entry<String, String> e : cases.entrySet()) {
      Future<Long> f = ex.submit(() -> {
        long t0 = System.nanoTime();
        Kramdown.toHtml(e.getValue(), null);
        return System.nanoTime() - t0;
      });
      String r;
      try {
        r = String.format("%.4fs", f.get(20, TimeUnit.SECONDS) / 1e9);
      } catch (java.util.concurrent.TimeoutException te) {
        r = ">20s";
        f.cancel(true);
        ex.shutdownNow();
        ex = Executors.newSingleThreadExecutor(rr -> {
          Thread t = new Thread(null, rr, "bench", 512L << 20);
          t.setDaemon(true);
          return t;
        });
      } catch (java.util.concurrent.ExecutionException ee) {
        r = "ERROR " + ee.getCause();
      }
      System.out.printf("%-24s %s%n", e.getKey(), r);
    }
    System.exit(0);
  }
}
