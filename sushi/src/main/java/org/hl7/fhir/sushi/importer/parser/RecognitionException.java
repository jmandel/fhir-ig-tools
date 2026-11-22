package org.hl7.fhir.sushi.importer.parser;

/**
 * A syntax error caught by a rule (ANTLR NoViableAltException / InputMismatchException); recorded on the context that
 * caught it ({@link ParserRuleContext#exception}) like ANTLR does.
 */
public class RecognitionException extends RuntimeException {
  public enum Kind { NO_VIABLE_ALT, INPUT_MISMATCH }

  private final Kind kind;
  private final transient Token offendingToken;

  public RecognitionException(Kind kind, Token offendingToken) {
    super(null, null, false, false);
    this.kind = kind;
    this.offendingToken = offendingToken;
  }

  public Kind getKind() { return kind; }
  public Token getOffendingToken() { return offendingToken; }
}
