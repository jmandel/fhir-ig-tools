package org.hl7.fhir.sushi.importer.parser;

import java.util.List;

/** The lexer or parser that reports a syntax error. */
public interface Recognizer {
  /**
   * For a parser: the tokens its token stream has fetched so far, all channels, by token index (TS
   * {@code recognizer._input.tokens}, used by FSHErrorListener to look backwards). For a lexer: an empty list.
   */
  List<Token> getTokens();
}
