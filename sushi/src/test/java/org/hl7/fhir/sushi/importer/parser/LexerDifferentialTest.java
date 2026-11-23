package org.hl7.fhir.sushi.importer.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** The hand-written lexers produce exactly the JS runtime's tokens and lexer errors on every stored input. */
class LexerDifferentialTest {
  static String lexDump(String input, boolean mini) {
    CstDump.Recorder rec = new CstDump.Recorder();
    InputText text = new InputText(input);
    TokenSource lexer = mini ? new MiniFSHLexer(text, rec) : new FSHLexer(text, rec);
    CommonTokenStream ts = new CommonTokenStream(lexer);
    ts.fill();
    return "#tokens\n" + CstDump.tokens(ts.getTokens()) + "#lexerrors\n" + rec.out;
  }

  static String expected(String jsDump) {
    int e = jsDump.indexOf("#errors\n"), t = jsDump.indexOf("#tree\n");
    StringBuilder sb = new StringBuilder(jsDump.substring(0, e)).append("#lexerrors\n");
    for (String l : jsDump.substring(e + 8, t).split("\n")) {
      if (l.matches("\\d+:\\d+ lexer .*")) sb.append(l).append('\n');
    }
    return sb.toString();
  }

  static void check(String set, String ext) throws IOException {
    Path in = OracleCrossCheckTest.data().resolve(set), dumps = OracleCrossCheckTest.data().resolve("jsdump").resolve(set);
    Assumptions.assumeTrue(Files.isDirectory(in) && Files.isDirectory(dumps), "no " + set + " data");
    Assumptions.assumeTrue(Differential.heavy(set), "heavy set " + set + ": run with -Dwp1.heavy=true");
    List<Path> inputs;
    try (Stream<Path> s = Files.walk(in)) {
      inputs = s.filter(p -> p.toString().endsWith(ext) && Files.isRegularFile(p)).sorted().toList();
    }
    List<Differential.Diff> diffs = Collections.synchronizedList(new ArrayList<>());
    Differential.forEach(inputs, p -> {
      try {
        String exp = expected(Files.readString(dumps.resolve(in.relativize(p) + ".dump"), StandardCharsets.UTF_8));
        String act;
        try {
          act = lexDump(Differential.readInput(p, ext.equals(".fsh")), ext.equals(".mini"));
        } catch (RuntimeException e) {
          act = "#crash " + e;
        }
        if (!exp.equals(act)) diffs.add(Differential.firstDiff(p, exp, act));
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    });
    List<Differential.Diff> sorted = new ArrayList<>(diffs);
    sorted.sort((a, b) -> a.input().compareTo(b.input()));
    Differential.Report r = new Differential.Report(inputs.size(), sorted, List.of());
    System.out.println(r.summary("hand lexer vs JS [" + set + "]"));
    assertTrue(sorted.isEmpty(), r.summary(set));
  }

  @Test void corpus() throws IOException { check("corpus", ".fsh"); }

  @Test void fixtures() throws IOException { check("fixtures", ".fsh"); }

  @Test void fuzz() throws IOException { check("fuzz", ".fsh"); }

  @Test void mini() throws IOException { check("mini", ".mini"); }

  @Test void miniFuzz() throws IOException { check("minifuzz", ".mini"); }
}
