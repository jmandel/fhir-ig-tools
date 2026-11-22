package org.hl7.fhir.jekyll.rouge;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A Rouge lexer <em>class</em> (what Ruby's <code>Lexer.find</code> / <code>Lexer.guess</code> return):
 * its class-level metadata plus a constructor. Every Rouge 4.7.0 lexer has one (from
 * {@link LexerData}); those whose rules are not ported construct an {@link UnportedLexer}.
 */
public final class LexerClass {
  public final String className;
  public final String tag;
  public final List<String> aliases;
  public final List<String> filenames;
  public final List<String> mimetypes;
  public final boolean detectable;
  public final String title;
  final Predicate<TextAnalyzer> detect;
  Function<Map<String, Object>, Lexer> factory; // null = not ported

  LexerClass(String className, String tag, List<String> aliases, List<String> filenames, List<String> mimetypes,
      boolean detectable, String title, Predicate<TextAnalyzer> detect) {
    this.className = className;
    this.tag = tag;
    this.aliases = aliases;
    this.filenames = filenames;
    this.mimetypes = mimetypes;
    this.detectable = detectable;
    this.title = title;
    this.detect = detect;
  }

  public boolean ported() {
    return factory != null;
  }

  /** lexer_class.new(opts) */
  public Lexer newLexer(Map<String, Object> opts) {
    return factory != null ? factory.apply(opts) : new UnportedLexer(this, opts);
  }

  public Lexer newLexer() {
    return newLexer(Map.of());
  }

  /** self.detect?(text) (only meaningful when {@link #detectable}). */
  public boolean detect(TextAnalyzer text) {
    return detect != null && detect.test(text);
  }

  /** Class-level <code>Lexer.lex(stream, opts={})</code>: <code>new(opts).lex(stream)</code>. */
  public void lex(String text, Tok.Sink sink) {
    newLexer().lex(text, sink);
  }

  /** Class-level <code>continue_lex</code> = a fresh lex (there is no state). */
  public void continueLex(String text, Tok.Sink sink) {
    lex(text, sink);
  }

  @Override
  public String toString() {
    return "Rouge::Lexers::" + className;
  }
}
