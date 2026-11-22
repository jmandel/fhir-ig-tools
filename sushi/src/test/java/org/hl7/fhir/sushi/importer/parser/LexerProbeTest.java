package org.hl7.fhir.sushi.importer.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Dev aid: prints oracle tokens for probe inputs (-Dwp1.probe=<file with one JSON-ish escaped input per line>). */
class LexerProbeTest {
  @Test void probe() throws Exception {
    String in = System.getProperty("wp1.probe");
    if (in == null) return;
    StringBuilder sb = new StringBuilder();
    for (String line : Files.readAllLines(Path.of(in))) {
      String s = line.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r");
      CstDump.Recorder rec = new CstDump.Recorder();
      List<Token> toks = AntlrOracle.parseDoc(s, rec).tokens();
      sb.append("== ").append(line).append('\n');
      for (Token t : toks) sb.append("   ").append(FSHParser.displayName(t.getType())).append(' ').append(CstDump.token(t)).append('\n');
      sb.append(rec.out);
    }
    Files.writeString(Path.of(in + ".out"), sb.toString());
  }
}
