package org.hl7.fhir.jekyll.rouge;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ports of the Rouge 4.7.0 HTML formatters used by kramdown and Jekyll:
 * formatters/html.rb, html_legacy.rb, html_pygments.rb, html_table.rb, html_line_highlighter.rb.
 * Option maps use kramdown's symbol names as strings ("css_class", "wrap", "line_numbers", ...).
 */
public final class Formatters {
  private Formatters() {}

  /** Rouge::Formatters::HTML */
  public static class HTML extends Formatter {
    @Override
    public void stream(List<Tok> tokens, Consumer<String> b) {
      for (Tok t : tokens) {
        b.accept(span(t.token(), t.val()));
      }
    }

    public String span(Token tok, String val) {
      // escape?(tok) cannot be true after filter_escapes
      return safeSpan(tok, escapeSpecialHtmlChars(val));
    }

    public String safeSpan(Token tok, String safeVal) {
      if (tok == Tokens.Text) {
        return safeVal;
      }
      return "<span class=\"" + tok.shortname + "\">" + safeVal + "</span>";
    }

    /** escape_special_html_chars: &amp; &lt; &gt; only */
    public static String escapeSpecialHtmlChars(String value) {
      StringBuilder b = null;
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        String r = c == '&' ? "&amp;" : c == '<' ? "&lt;" : c == '>' ? "&gt;" : null;
        if (r != null) {
          if (b == null) {
            b = new StringBuilder(value.length() + 16);
            b.append(value, 0, i);
          }
          b.append(r);
        } else if (b != null) {
          b.append(c);
        }
      }
      return b == null ? value : b.toString();
    }
  }

  /** Rouge::Formatters::HTMLPygments */
  public static final class HTMLPygments extends Formatter {
    final Formatter inner;
    final String cssClass;

    public HTMLPygments(Formatter inner, String cssClass) {
      this.inner = inner;
      this.cssClass = cssClass;
    }

    @Override
    public void stream(List<Tok> tokens, Consumer<String> b) {
      b.accept("<div class=\"highlight\"><pre class=\"" + cssClass + "\"><code>");
      inner.stream(tokens, b);
      b.accept("</code></pre></div>");
    }
  }

  /** Rouge::Formatters::HTMLTable */
  public static final class HTMLTable extends Formatter {
    final Formatter inner;
    final long startLine;
    final String lineFormat;
    final String tableClass, gutterClass, codeClass;

    public HTMLTable(Formatter inner, Map<String, Object> opts) {
      this.inner = inner;
      Object sl = opts.getOrDefault("start_line", 1L);
      this.startLine = sl instanceof Number ? ((Number) sl).longValue() : Long.parseLong(sl.toString());
      this.lineFormat = String.valueOf(opts.getOrDefault("line_format", "%i"));
      this.tableClass = String.valueOf(opts.getOrDefault("table_class", "rouge-table"));
      this.gutterClass = String.valueOf(opts.getOrDefault("gutter_class", "rouge-gutter"));
      this.codeClass = String.valueOf(opts.getOrDefault("code_class", "rouge-code"));
    }

    @Override
    public void stream(List<Tok> tokens, Consumer<String> b) {
      String lastVal = null;
      long numLines = 0;
      for (Tok t : tokens) {
        lastVal = t.val();
        for (int k = 0; k < lastVal.length(); k++) {
          if (lastVal.charAt(k) == '\n') {
            numLines++;
          }
        }
      }
      StringBuilder formatted = new StringBuilder(inner.format(tokens));
      if (!(lastVal != null && lastVal.endsWith("\n"))) {
        numLines += 1;
        formatted.append('\n');
      }
      StringBuilder nums = new StringBuilder();
      for (long i = startLine; i <= startLine + numLines - 1; i++) {
        if (i > startLine) {
          nums.append('\n');
        }
        nums.append(sprintf(lineFormat, i));
      }
      nums.append('\n');
      b.accept("<table class=\"" + tableClass + "\"><tbody><tr>" + "<td class=\"" + gutterClass + " gl\">"
          + "<pre class=\"lineno\">" + nums + "</pre>" + "</td>" + "<td class=\"" + codeClass + "\"><pre>" + formatted
          + "</pre></td>" + "</tr></tbody></table>");
    }

    private static String sprintf(String fmt, long i) {
      if (fmt.equals("%i") || fmt.equals("%d")) {
        return Long.toString(i);
      }
      return String.format(java.util.Locale.ROOT, fmt.replace("%i", "%d"), i);
    }
  }

  /** Rouge::Formatters::HTMLLineHighlighter */
  public static final class HTMLLineHighlighter extends Formatter {
    final Formatter delegate;
    final String highlightLineClass;
    final List<Long> highlightLines;

    public HTMLLineHighlighter(Formatter delegate, Map<String, Object> opts) {
      this.delegate = delegate;
      this.highlightLineClass = String.valueOf(opts.getOrDefault("highlight_line_class", "hll"));
      @SuppressWarnings("unchecked")
      List<Long> hl = (List<Long>) opts.get("highlight_lines");
      this.highlightLines = hl == null ? List.of() : hl;
    }

    @Override
    public void stream(List<Tok> tokens, Consumer<String> b) {
      long[] lineno = {1};
      tokenLines(tokens, lineTokens -> {
        String line = delegate.format(lineTokens) + "\n";
        if (highlightLines.contains(lineno[0])) {
          line = "<span class=\"" + highlightLineClass + "\">" + line + "</span>";
        }
        lineno[0]++;
        b.accept(line);
      });
    }
  }

  /** Rouge::Formatters::HTMLLegacy, as kramdown uses it (inline_theme not ported). */
  public static final class HTMLLegacy extends Formatter {
    final Formatter formatter;

    public HTMLLegacy(Map<String, Object> opts) {
      if (truthy(opts.get("inline_theme"))) {
        throw new UnsupportedOperationException("Rouge HTMLLegacy :inline_theme is not ported");
      }
      Formatter f = new HTML();
      if (truthy(opts.get("line_numbers"))) {
        f = new HTMLTable(f, opts);
      }
      Object wrap = opts.containsKey("wrap") ? opts.get("wrap") : Boolean.TRUE;
      if (truthy(wrap)) {
        Object css = opts.containsKey("css_class") ? opts.get("css_class") : "codehilite";
        f = new HTMLPygments(f, css == null ? "" : String.valueOf(css));
      }
      this.formatter = f;
    }

    @Override
    public void stream(List<Tok> tokens, Consumer<String> b) {
      formatter.stream(tokens, b);
    }
  }

  static boolean truthy(Object o) {
    return o != null && !Boolean.FALSE.equals(o);
  }
}
