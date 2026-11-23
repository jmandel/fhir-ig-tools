package org.hl7.fhir.sushi.importer.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The WP-1 gate: the hand-written parser produces exactly the JS runtime's tokens, syntax errors (text, order,
 * position) and parse tree (incl. error nodes, recovered structure, start/stop tokens, caught exceptions) on every
 * stored input set (corpus, SUSHI fixtures + Jest inputs, fuzz).
 */
class HandParserDifferentialTest {
  static String fshDump(String input) {
    CstDump.Recorder rec = new CstDump.Recorder();
    HandParserBackend.Result<FSHParser.DocContext> r = HandParserBackend.parseDocWithTokens(input, rec);
    return CstDump.full(r.tokens(), rec, r.tree(), FSHParser.ruleNames);
  }

  static String miniDump(String input) {
    CstDump.Recorder rec = new CstDump.Recorder();
    HandParserBackend.Result<MiniFSHParser.RuleSetContext> r = HandParserBackend.parseMiniWithTokens(input, rec);
    return CstDump.full(r.tokens(), rec, r.tree(), MiniFSHParser.ruleNames);
  }

  static void check(String set, String ext) throws IOException {
    Path in = OracleCrossCheckTest.data().resolve(set), dumps = OracleCrossCheckTest.data().resolve("jsdump").resolve(set);
    Assumptions.assumeTrue(Files.isDirectory(in) && Files.isDirectory(dumps), "no " + set + " data");
    Assumptions.assumeTrue(Differential.heavy(set), "heavy set " + set + ": run with -Dwp1.heavy=true");
    Differential.Report r = Differential.compare(in, dumps, ext,
        ext.equals(".mini") ? HandParserDifferentialTest::miniDump : HandParserDifferentialTest::fshDump);
    System.out.println(r.summary("hand parser vs JS [" + set + "]"));
    assertTrue(r.diffs().isEmpty() && r.missing().isEmpty(), r.summary(set));
  }

  @Test void corpus() throws IOException { check("corpus", ".fsh"); }

  @Test void fixtures() throws IOException { check("fixtures", ".fsh"); }

  @Test void fuzz() throws IOException { check("fuzz", ".fsh"); }

  @Test void mini() throws IOException { check("mini", ".mini"); }

  @Test void miniFuzz() throws IOException { check("minifuzz", ".mini"); }
}
