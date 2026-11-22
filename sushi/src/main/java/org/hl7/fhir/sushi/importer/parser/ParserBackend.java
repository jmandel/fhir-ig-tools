package org.hl7.fhir.sushi.importer.parser;

/** A parser implementation producing the CST: the hand-written one, or (tests only) the ANTLR oracle adapter. */
public interface ParserBackend {
  FSHParser.DocContext parseDoc(String input, ErrorListener listener);

  MiniFSHParser.RuleSetContext parseMiniFsh(String input, ErrorListener listener);
}
