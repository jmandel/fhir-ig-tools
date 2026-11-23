package org.hl7.fhir.sushi.importer.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Dev aid (-Dwp1.speed=true): single-thread parse time of the corpus, hand-written vs ANTLR (Java) parser. */
class ParserSpeedTest {
  @Test void speed() throws Exception {
    if (!Boolean.getBoolean("wp1.speed")) return;
    List<String> inputs;
    try (Stream<Path> s = Files.walk(OracleCrossCheckTest.data().resolve("corpus"))) {
      inputs = s.filter(p -> p.toString().endsWith(".fsh")).sorted().map(p -> Differential.readInput(p, true)).toList();
    }
    long chars = inputs.stream().mapToLong(String::length).sum();
    ErrorListener none = (r, t, l, c, m) -> {};
    for (int round = 0; round < 3; round++) {
      long tl = System.nanoTime();
      for (String in : inputs) new CommonTokenStream(new FSHLexer(new InputText(in), none)).fill();
      long t0 = System.nanoTime();
      System.out.printf("lex only %.1fs%n", (t0 - tl) / 1e9);
      for (String in : inputs) HandParserBackend.parseDocWithTokens(in, none);
      long t1 = System.nanoTime();
      for (String in : inputs) AntlrOracle.parseDoc(in, none);
      long t2 = System.nanoTime();
      System.out.printf("round %d: %d files, %d MB: hand %.1fs, antlr %.1fs%n", round, inputs.size(), chars >> 20,
          (t1 - t0) / 1e9, (t2 - t1) / 1e9);
    }
  }
}
