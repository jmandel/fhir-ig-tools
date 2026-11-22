package org.hl7.fhir.jekyll.rouge;

/** One <code>[token, chunk]</code> pair of a Rouge token stream. */
public record Tok(Token token, String val) {
  /** Receiver of a token stream (the block passed to Ruby's lex/stream_tokens). */
  public interface Sink {
    void token(Token tok, String val);
  }
}
