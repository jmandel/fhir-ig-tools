package org.hl7.fhir.sushi.importer.parser;

/**
 * Test-scope parser backend on the ANTLR oracle (see {@link AntlrOracle}); selected by {@code -Dsushi.parser=antlr}
 * or {@code Parsers.setBackend(new AntlrCstBackend())}.
 */
public final class AntlrCstBackend implements ParserBackend {
  @Override public FSHParser.DocContext parseDoc(String input, ErrorListener listener) {
    return AntlrOracle.parseDoc(input, listener).tree();
  }

  @Override public MiniFSHParser.RuleSetContext parseMiniFsh(String input, ErrorListener listener) {
    return AntlrOracle.parseMini(input, listener).tree();
  }
}
