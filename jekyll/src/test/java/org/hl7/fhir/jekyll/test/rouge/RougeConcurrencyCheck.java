package org.hl7.fhir.jekyll.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Cold-start thread-safety check: renders "rouge" cases concurrently first (so lazy state/regex
 * initialization races would show), 3x shuffled, then sequentially, and compares.
 * usage: RougeConcurrencyCheck &lt;cases.jsonl&gt; [threads]
 */
public final class RougeConcurrencyCheck {
  public static void main(String[] args) throws Exception {
    List<Map<String, Object>> cases = DiffRunner.readJsonl(Path.of(args[0]));
    int threads = args.length > 1 ? Integer.parseInt(args[1]) : 8;
    List<Integer> order = new ArrayList<>();
    for (int r = 0; r < 3; r++) {
      for (int i = 0; i < cases.size(); i++) {
        order.add(i);
      }
    }
    Collections.shuffle(order, new Random(42));
    ExecutorService ex = Executors.newFixedThreadPool(threads);
    List<Future<String>> fs = new ArrayList<>();
    for (int i : order) {
      fs.add(ex.submit(() -> String.valueOf(RougeCaseRunner.run(cases.get(i)))));
    }
    List<String> conc = new ArrayList<>();
    for (Future<String> f : fs) {
      conc.add(f.get());
    }
    ex.shutdown();
    int bad = 0;
    for (int k = 0; k < order.size(); k++) {
      if (!conc.get(k).equals(String.valueOf(RougeCaseRunner.run(cases.get(order.get(k)))))) {
        bad++;
      }
    }
    System.out.println("concurrent renders=" + order.size() + " threads=" + threads + " mismatches=" + bad);
  }
}
