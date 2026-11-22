package org.hl7.fhir.sushi.importer.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Checks the Java ANTLR oracle (AntlrOracle) against SUSHI's real JS parser: identical tokens, errors (text, order,
 * position) and trees on every input that has a JS dump. Inputs/dumps live on the VM disk (skipped when absent):
 * {@code -Dwp1.data=/srv/ig/sushi-wp1} with {@code corpus/}, {@code jsdump/corpus/}, ...
 */
class OracleCrossCheckTest {
  static final Path DATA = Paths.get(System.getProperty("wp1.data", "/srv/ig/sushi-wp1"));

  static void check(String set, String ext) throws IOException {
    Path in = DATA.resolve(set), dumps = DATA.resolve("jsdump").resolve(set);
    Assumptions.assumeTrue(Files.isDirectory(in) && Files.isDirectory(dumps), "no " + set + " data");
    Differential.Report r = Differential.compare(in, dumps, ext,
        ext.equals(".mini") ? Differential::miniDump : s -> Differential.fshDump(s, AntlrOracle::parseDoc));
    System.out.println(r.summary("ANTLR oracle vs JS [" + set + "]"));
    assertTrue(r.diffs().isEmpty() && r.missing().isEmpty(), r.summary(set));
  }

  @Test void corpus() throws IOException { check("corpus", ".fsh"); }

  @Test void fixtures() throws IOException { check("fixtures", ".fsh"); }

  @Test void fuzz() throws IOException { check("fuzz", ".fsh"); }

  @Test void mini() throws IOException { check("mini", ".mini"); }

  @Test void miniFuzz() throws IOException { check("minifuzz", ".mini"); }
}
