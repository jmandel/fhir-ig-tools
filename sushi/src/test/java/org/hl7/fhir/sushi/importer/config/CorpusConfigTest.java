package org.hl7.fhir.sushi.importer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * WP-3 gate, part 1: every sushi-config.yaml of the corpus (work/sushi-spike/fsh-repos.txt, 266 configs) parses to
 * the same YAML value, configuration object and log messages as TS SUSHI 3.21.1 (node oracle
 * scripts/oracle/config-oracle.js). Skips when the oracle data (VM) is absent.
 */
class CorpusConfigTest {
  @Test void corpusConfigsMatchTs() throws Exception {
    Path in = ConfigOracle.dir().resolve("corpus-inputs.json"), out = ConfigOracle.dir().resolve("corpus-oracle.json");
    assumeTrue(Files.isRegularFile(in) && Files.isRegularFile(out), "no corpus oracle data");
    List<ConfigOracle.Result> results = ConfigOracle.run(in, out);
    StringBuilder report = new StringBuilder();
    for (ConfigOracle.Result r : results) {
      for (String d : r.diffs()) report.append(r.name()).append(": ").append(d).append('\n');
      for (String d : r.typedDiffs()) report.append(r.name()).append(": ").append(d).append('\n');
    }
    System.out.println("CorpusConfigTest: " + ConfigOracle.summary(results));
    if (report.length() > 0) System.out.println(report);
    assertEquals("", report.toString());
  }
}
