package org.hl7.fhir.sushi.importer.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/** Differential comparison of parser dumps against stored JS-runtime dumps (scripts/wp1/js-oracle.sh). */
final class Differential {
  private Differential() {}

  record Diff(Path input, int line, String expected, String actual) {
    @Override public String toString() {
      return input + " @dump line " + line + "\n    js:   " + expected + "\n    java: " + actual;
    }
  }

  record Report(int files, List<Diff> diffs, List<Path> missing) {
    String summary(String label) {
      StringBuilder sb = new StringBuilder(label + ": " + files + " files, " + diffs.size() + " differ, "
          + missing.size() + " without JS dump");
      diffs.stream().limit(25).forEach(d -> sb.append("\n  ").append(d));
      return sb.toString();
    }
  }

  static String readInput(Path p, boolean fsh) {
    try {
      // like node's fs.readFileSync(p, 'utf8'): malformed bytes become U+FFFD
      String s = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
      return fsh && !s.endsWith("\n") ? s + "\n" : s;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String fshDump(String input, ParserBackendWithTokens parser) {
    CstDump.Recorder rec = new CstDump.Recorder();
    AntlrOracle.Result<FSHParser.DocContext> r = parser.parse(input, rec);
    return CstDump.full(r.tokens(), rec, r.tree(), FSHParser.ruleNames);
  }

  static String miniDump(String input) {
    CstDump.Recorder rec = new CstDump.Recorder();
    AntlrOracle.Result<MiniFSHParser.RuleSetContext> r = AntlrOracle.parseMini(input, rec);
    return CstDump.full(r.tokens(), rec, r.tree(), MiniFSHParser.ruleNames);
  }

  interface ParserBackendWithTokens {
    AntlrOracle.Result<FSHParser.DocContext> parse(String input, ErrorListener listener);
  }

  /** Compares dump(input) with the JS dump for every file with the extension under inputRoot. */
  static Report compare(Path inputRoot, Path dumpRoot, String ext, Function<String, String> dumper) throws IOException {
    List<Path> inputs;
    try (Stream<Path> s = Files.walk(inputRoot)) {
      inputs = s.filter(p -> p.toString().endsWith(ext) && Files.isRegularFile(p)).sorted().toList();
    }
    List<Diff> diffs = Collections.synchronizedList(new ArrayList<>());
    List<Path> missing = Collections.synchronizedList(new ArrayList<>());
    inputs.parallelStream().forEach(p -> {
      Path d = dumpRoot.resolve(inputRoot.relativize(p).toString() + ".dump");
      if (!Files.exists(d)) {
        missing.add(p);
        return;
      }
      try {
        String expected = Files.readString(d, StandardCharsets.UTF_8);
        String actual;
        try {
          actual = dumper.apply(readInput(p, ext.equals(".fsh")));
        } catch (RuntimeException e) {
          actual = "#crash\n" + e;
        }
        if (!expected.equals(actual)) diffs.add(firstDiff(p, expected, actual));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    List<Diff> sorted = new ArrayList<>(diffs);
    sorted.sort((a, b) -> a.input().compareTo(b.input()));
    return new Report(inputs.size(), sorted, missing);
  }

  static Diff firstDiff(Path p, String expected, String actual) {
    String[] e = expected.split("\n", -1), a = actual.split("\n", -1);
    int i = 0;
    while (i < e.length && i < a.length && e[i].equals(a[i])) i++;
    return new Diff(p, i + 1, i < e.length ? e[i] : "<end>", i < a.length ? a[i] : "<end>");
  }
}
