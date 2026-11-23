package org.hl7.fhir.sushi.importer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * WP-3 gate, part 2: the mutation set (scripts/oracle/config-mutations.py: textual mutations of corpus configs,
 * wrong-typed values, hand-written YAML edge cases) against TS SUSHI 3.21.1. Every difference is classified:
 * <ul>
 *   <li>{@code yaml-wording}: both reject the YAML, with different error text (libyaml vs yaml@1.10 wording);
 *   <li>{@code yaml-ts-only-rejects} / {@code yaml-java-only-rejects}: only one side rejects the YAML;
 *   <li>{@code yaml-value}: both parse, to different values;
 *   <li>{@code import}: same YAML value but different configuration, log or exception (a port bug);
 *   <li>{@code typed-lossy}: identical TS-shaped configuration, but the typed Configuration cannot hold a raw value
 *       of an unexpected type (documented in ConfigurationJs).
 * </ul>
 * The test fails on any {@code import} difference; the classified report is written next to the oracle data
 * (mutation-report.tsv) and summarized on stdout.
 */
class MutationConfigTest {
  @Test void mutationSetDifferencesAreClassified() throws Exception {
    Path in = ConfigOracle.dir().resolve("mutation-inputs.json"), out = ConfigOracle.dir().resolve("mutation-oracle.json");
    assumeTrue(Files.isRegularFile(in) && Files.isRegularFile(out), "no mutation oracle data");
    List<ConfigOracle.Result> results = ConfigOracle.run(in, out);
    Map<String, Integer> counts = new TreeMap<>();
    StringBuilder tsv = new StringBuilder("class\tname\tdetail\n");
    StringBuilder bugs = new StringBuilder();
    for (ConfigOracle.Result r : results) {
      String cls = classify(r);
      counts.merge(cls, 1, Integer::sum);
      if (!cls.equals("identical")) {
        String detail = String.join(" | ", r.diffs().isEmpty() ? r.typedDiffs() : r.diffs()).replace('\n', ' ').replace('\t', ' ');
        tsv.append(cls).append('\t').append(r.name()).append('\t').append(detail).append('\n');
        if (cls.equals("import")) bugs.append(r.name()).append(": ").append(detail).append('\n');
      }
    }
    try {
      Files.writeString(ConfigOracle.dir().resolve("mutation-report.tsv"), tsv);
    } catch (java.io.IOException e) {
      System.out.println("MutationConfigTest: cannot write report: " + e);
    }
    System.out.println("MutationConfigTest: " + results.size() + " inputs " + counts);
    assertEquals("", bugs.toString());
  }

  static String classify(ConfigOracle.Result r) {
    if (r.diffs().isEmpty()) return r.typedDiffs().isEmpty() ? "identical" : "typed-lossy";
    for (String d : r.diffs()) {
      if (d.startsWith("yaml: error message")) return "yaml-wording";
      if (d.startsWith("yaml: TS error")) return "yaml-ts-only-rejects";
      if (d.startsWith("yaml: Java error")) return "yaml-java-only-rejects";
      if (d.startsWith("yaml: value differs")) return "yaml-value";
    }
    return "import";
  }
}
