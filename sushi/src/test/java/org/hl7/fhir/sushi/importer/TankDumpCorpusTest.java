package org.hl7.fhir.sushi.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Writes the Java half of the WP-2 gate for every IG under -Dwp2.corpus=<dir of IG/input/fsh trees> into
 * -Dwp2.out=<dir>/<IG>/ (see TankDump). Skipped unless both properties are set; scripts/tank-dump.sh drives it.
 */
class TankDumpCorpusTest {
  @Test void dumpCorpus() throws IOException {
    String corpus = System.getProperty("wp2.corpus");
    String out = System.getProperty("wp2.out");
    Assumptions.assumeTrue(corpus != null && out != null, "wp2.corpus / wp2.out not set");
    String only = System.getProperty("wp2.only", "");
    List<Path> igs;
    try (Stream<Path> s = Files.list(Path.of(corpus))) {
      igs = s.filter(p -> only.isEmpty() || List.of(only.split(",")).contains(p.getFileName().toString())).sorted().toList();
    }
    int n = 0;
    for (Path ig : igs) {
      Path fsh = ig.resolve("input/fsh");
      if (!Files.isDirectory(fsh)) continue;
      try {
        TankDump.run(fsh, Path.of(out, ig.getFileName().toString()));
      } catch (RuntimeException e) {
        Files.createDirectories(Path.of(out, ig.getFileName().toString()));
        Files.writeString(Path.of(out, ig.getFileName().toString(), "crash.txt"), String.valueOf(e) + "\n");
        e.printStackTrace();
      }
      n++;
    }
    System.out.println("TankDumpCorpusTest: " + n + " IGs");
  }
}
