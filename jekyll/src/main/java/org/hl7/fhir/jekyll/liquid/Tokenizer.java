package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.List;

/** Port of liquid-4.0.4/lib/liquid/tokenizer.rb. */
public class Tokenizer {
  private final List<String> tokens;
  private int pos;
  private Integer lineNumber;

  public Tokenizer(String source, boolean lineNumbers) {
    this.lineNumber = lineNumbers ? 1 : null;
    this.tokens = tokenize(source);
  }

  public Integer lineNumber() {
    return lineNumber;
  }

  /** Ruby {@code shift}: next token or null; advances the line counter by the token's newlines. */
  public String shift() {
    if (pos >= tokens.size()) {
      return null;
    }
    String token = tokens.get(pos);
    tokens.set(pos++, null);
    if (lineNumber != null) {
      int n = 0;
      for (int i = 0; i < token.length(); i++) {
        if (token.charAt(i) == '\n') {
          n++;
        }
      }
      lineNumber += n;
    }
    return token;
  }

  /**
   * Ruby {@code source.split(TemplateParser)} (captures kept, trailing empties dropped), minus a
   * leading "". TemplateParser is {@code /({%.*?%}|{{.*?}}?|{%|{{)/m}; java.util.regex runs that
   * lazy alternation recursively per character (the dominant cost on large raw-wrapped includes,
   * and a stack-overflow risk), so the identical leftmost-first semantics are scanned by hand:
   * a match starts at the first "{%" or "{{"; "{%" extends to the first following "%}" (else is
   * just "{%"); "{{" extends to the first following "}" plus one more "}" if present (else is
   * just "{{").
   */
  private static List<String> tokenize(String source) {
    List<String> out = new ArrayList<>();
    if (source == null || source.isEmpty()) {
      return out;
    }
    int n = source.length();
    int last = 0;
    int i = source.indexOf('{');
    int nextTagEnd = -2; // cached result of indexOf("%}", from), monotonic in from
    int nextBrace = -2; // cached result of indexOf('}', from)
    while (i >= 0 && i + 1 < n) {
      char c = source.charAt(i + 1);
      int end = -1;
      if (c == '%') {
        if (nextTagEnd != -1 && nextTagEnd < i + 2) {
          nextTagEnd = source.indexOf("%}", i + 2);
        }
        end = nextTagEnd >= 0 ? nextTagEnd + 2 : i + 2;
      } else if (c == '{') {
        if (nextBrace != -1 && nextBrace < i + 2) {
          nextBrace = source.indexOf('}', i + 2);
        }
        if (nextBrace >= 0) {
          end = nextBrace + 1 < n && source.charAt(nextBrace + 1) == '}' ? nextBrace + 2 : nextBrace + 1;
        } else {
          end = i + 2;
        }
      }
      if (end < 0) {
        i = source.indexOf('{', i + 1);
        continue;
      }
      out.add(source.substring(last, i));
      out.add(source.substring(i, end));
      last = end;
      i = source.indexOf('{', end);
    }
    out.add(source.substring(last));
    while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
      out.remove(out.size() - 1);
    }
    if (!out.isEmpty() && out.get(0).isEmpty()) {
      out.remove(0);
    }
    return out;
  }
}
