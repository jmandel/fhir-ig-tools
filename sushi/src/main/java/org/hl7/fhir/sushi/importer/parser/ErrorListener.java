package org.hl7.fhir.sushi.importer.parser;

/** Receives syntax errors from the lexer and parser (ANTLR ErrorListener.syntaxError). */
@FunctionalInterface
public interface ErrorListener {
  /**
   * @param recognizer the lexer or parser reporting the error
   * @param offendingSymbol the offending token; null for lexer errors
   * @param line 1-based line
   * @param column 0-based UTF-16 column
   * @param msg ANTLR's message ("mismatched input ... expecting ...", "token recognition error at: ...", ...)
   */
  void syntaxError(Recognizer recognizer, Token offendingSymbol, int line, int column, String msg);

  /**
   * ANTLR's ConsoleErrorListener (JS: {@code console.error("line " + line + ":" + column + " " + msg)}), on the CLI's
   * stderr (the output stream when sushi runs in process).
   */
  ErrorListener CONSOLE = (r, t, line, column, msg) -> org.hl7.fhir.sushi.App.stderr()
      .println("line " + line + ":" + column + " " + msg);
}
