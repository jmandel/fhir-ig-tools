package org.hl7.fhir.jekyll.rouge;

import java.util.Map;

/**
 * Stand-in for a Rouge lexer whose rules are not ported (see NOTES-rouge.md): the whole input is
 * one Text token, i.e. the escaped text without token spans. Tag, aliases and guessing behave like
 * the real class; only the highlighting differs from Ruby.
 */
final class UnportedLexer extends Lexer {
  private final LexerClass cls;

  UnportedLexer(LexerClass cls, Map<String, Object> opts) {
    super(opts);
    this.cls = cls;
  }

  @Override
  public LexerClass lexerClass() {
    return cls;
  }

  @Override
  protected void streamTokens(String string, Tok.Sink b) {
    b.token(Text, string);
  }
}
