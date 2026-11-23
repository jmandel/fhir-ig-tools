package org.hl7.fhir.sushi.importer.parser;

/**
 * A syntax error caught by a rule (ANTLR NoViableAltException / InputMismatchException); recorded on the context that
 * caught it ({@link ParserRuleContext#exception}) like ANTLR does.
 */
public class RecognitionException extends RuntimeException {
  public enum Kind { NO_VIABLE_ALT, INPUT_MISMATCH }

  private final Kind kind;
  private final transient Token offendingToken;
  /** NO_VIABLE_ALT: the first token of the failed decision. */
  final transient Token startToken;
  /** the parser state and rule context when the error was detected (for the expected-token set). */
  final int offendingState;
  final transient ParserRuleContext ctx;

  public RecognitionException(Kind kind, Token offendingToken) {
    this(kind, offendingToken, offendingToken, -1, null);
  }

  RecognitionException(Kind kind, Token offendingToken, Token startToken, int offendingState, ParserRuleContext ctx) {
    super(null, null, false, false);
    this.kind = kind;
    this.offendingToken = offendingToken;
    this.startToken = startToken;
    this.offendingState = offendingState;
    this.ctx = ctx;
  }

  public Kind getKind() { return kind; }
  public Token getOffendingToken() { return offendingToken; }
}
