package org.hl7.fhir.sushi.importer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import org.antlr.v4.runtime.*;
import org.hl7.fhir.sushi.importer.antlr.*;
import org.junit.jupiter.api.Test;

/** Confirms the ANTLR Java target generated from SUSHI's .g4 files parses real FSH without syntax errors. */
class GrammarSmokeTest {
  static int parse(String text, List<String> errs) {
    FSHLexer lexer = new FSHLexer(CharStreams.fromString(text));
    FSHParser parser = new FSHParser(new CommonTokenStream(lexer));
    BaseErrorListener l = new BaseErrorListener() {
      @Override public void syntaxError(Recognizer<?, ?> r, Object o, int line, int col, String msg, RecognitionException e) {
        errs.add(line + ":" + col + " " + msg);
      }
    };
    lexer.removeErrorListeners(); parser.removeErrorListeners();
    lexer.addErrorListener(l); parser.addErrorListener(l);
    FSHParser.DocContext doc = parser.doc();
    return doc.entity().size();
  }

  @Test void parsesInlineFsh() {
    List<String> errs = new ArrayList<>();
    int n = parse("""
        Alias: LNC = http://loinc.org
        Profile: MyObs
        Parent: Observation
        Id: my-obs
        Title: "My Observation"
        * status MS
        * code = LNC#1234-5 "Thing"
        * value[x] only Quantity
        * component contains a 0..1 and b 1..*
        Instance: ex1
        InstanceOf: MyObs
        Usage: #example
        * status = #final
        * valueQuantity = 5.0 'mg' "milligram"
        """, errs);
    assertEquals(List.of(), errs);
    assertEquals(3, n);
  }

  @Test void reportsSyntaxErrors() {
    List<String> errs = new ArrayList<>();
    parse("Profile: Foo\nParent Observation\n", errs);
    assertFalse(errs.isEmpty());
  }

  /** All .fsh files under the given VM dirs (TS SUSHI's test fixtures and a corpus IG); skipped when absent. */
  @Test void parsesRealFshFiles() throws IOException {
    List<Path> roots = Stream.of(System.getProperty("sushi.ts.dir", "") + "/test",
            "/srv/ig/sushi-spike/src/HL7__genomics-reporting/input/fsh")
        .map(Paths::get).filter(Files::isDirectory).toList();
    org.junit.jupiter.api.Assumptions.assumeFalse(roots.isEmpty(), "no FSH sources on this machine");
    int files = 0, entities = 0;
    List<String> failures = new ArrayList<>();
    for (Path root : roots) {
      try (Stream<Path> s = Files.walk(root)) {
        for (Path p : s.filter(f -> f.toString().endsWith(".fsh")).sorted().toList()) {
          List<String> errs = new ArrayList<>();
          entities += parse(Files.readString(p, StandardCharsets.UTF_8), errs);
          files++;
          if (!errs.isEmpty()) failures.add(p + ": " + errs.get(0));
        }
      }
    }
    System.out.println("GrammarSmokeTest: " + files + " files, " + entities + " entities, " + failures.size() + " with syntax errors");
    failures.stream().limit(20).forEach(f -> System.out.println("  " + f));
    assertTrue(files > 0);
    // Some SUSHI test fixtures are intentionally invalid; the corpus IG must parse cleanly.
    assertTrue(failures.stream().noneMatch(f -> f.contains("genomics-reporting")), String.join("\n", failures));
  }
}
