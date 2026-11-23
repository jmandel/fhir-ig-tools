package org.hl7.fhir.jekyll.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Times each "rouge" case (after one warm-up pass); prints the slowest. usage: RougeBench &lt;cases.jsonl&gt; [top] */
public final class RougeBench {
  public static void main(String[] args) throws Exception {
    List<Map<String, Object>> cases = DiffRunner.readJsonl(Path.of(args[0]));
    int top = args.length > 1 ? Integer.parseInt(args[1]) : 20;
    for (Map<String, Object> c : cases) {
      RougeCaseRunner.run(c);
    }
    List<Object[]> times = new ArrayList<>();
    long total = 0;
    for (Map<String, Object> c : cases) {
      long t0 = System.nanoTime();
      RougeCaseRunner.run(c);
      long dt = System.nanoTime() - t0;
      total += dt;
      times.add(new Object[] {dt, c.get("id"), ((String) c.get("text")).length()});
    }
    times.sort((a, b) -> Long.compare((Long) b[0], (Long) a[0]));
    System.out.printf("total %.1f ms over %d cases%n", total / 1e6, cases.size());
    for (Object[] t : times.subList(0, Math.min(top, times.size()))) {
      System.out.printf("%9.1f ms  %8d chars  %s%n", (Long) t[0] / 1e6, t[2], t[1]);
    }
  }
}
