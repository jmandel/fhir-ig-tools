package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.Tok;
import org.hl7.fhir.jekyll.rouge.Token;

/** Port of rouge-4.7.0/lib/rouge/lexers/plain_text.rb */
public class PlainText extends Lexer {
  public static final LexerClass CLASS = Lexer.byClassName("PlainText");

  public final Token token;

  public PlainText(Map<String, ?> opts) {
    super(opts);
    Token t = tokenOption("token", null);
    this.token = t != null ? t : Text;
  }

  @Override
  public LexerClass lexerClass() {
    return CLASS;
  }

  @Override
  protected void streamTokens(String string, Tok.Sink b) {
    b.token(token, string);
  }
}
