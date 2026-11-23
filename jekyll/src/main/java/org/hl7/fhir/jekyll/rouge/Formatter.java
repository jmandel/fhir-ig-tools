package org.hl7.fhir.jekyll.rouge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Port of rouge-4.7.0/lib/rouge/formatter.rb (Rouge::Formatter). Token streams are materialized
 * lists: Ruby re-enumerates <code>lexer.lex(text)</code> (re-lexing) where a formatter walks the
 * tokens twice, which yields the same tokens.
 */
public abstract class Formatter {
  /** format(tokens): Escape tokens become Error tokens (escaping is never enabled here). */
  public String format(List<Tok> tokens) {
    List<Tok> filtered = filterEscapes(tokens);
    StringBuilder out = new StringBuilder();
    stream(filtered, out::append);
    return out.toString();
  }

  static List<Tok> filterEscapes(List<Tok> tokens) {
    List<Tok> out = null;
    for (int k = 0; k < tokens.size(); k++) {
      Tok t = tokens.get(k);
      if (t.token() == Tokens.Escape) {
        if (out == null) {
          out = new ArrayList<>(tokens.subList(0, k));
        }
        out.add(new Tok(Tokens.Error, t.val()));
      } else if (out != null) {
        out.add(t);
      }
    }
    return out == null ? tokens : out;
  }

  /** @abstract stream(tokens) { |piece| } */
  public abstract void stream(List<Tok> tokens, Consumer<String> b);

  /** token_lines(tokens): split the stream into lines of [tok, val] (newlines dropped). */
  protected static void tokenLines(List<Tok> tokens, Consumer<List<Tok>> b) {
    List<Tok> out = new ArrayList<>();
    for (Tok t : tokens) {
      String val = t.val();
      int k = 0, n = val.length();
      while (k < n) {
        if (val.charAt(k) == '\n') {
          b.accept(out);
          out = new ArrayList<>();
          k++;
        } else {
          int e = val.indexOf('\n', k);
          if (e < 0) {
            e = n;
          }
          out.add(new Tok(t.token(), val.substring(k, e)));
          k = e;
        }
      }
    }
    // for inputs not ending in a newline
    if (!out.isEmpty()) {
      b.accept(out);
    }
  }
}
