package org.hl7.fhir.jekyll.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Formatter;
import org.hl7.fhir.jekyll.rouge.Formatters;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.Tok;
import org.hl7.fhir.jekyll.rouge.lexers.PlainText;

/**
 * Forwarding runner loaded by name from {@link DiffRunner}: registers the "rouge" case type
 * (see jekyll/oracle/oracle.rb): <code>find_fancy(lang, text) || PlainText</code>, then the formatter
 * ("html" = Jekyll's highlight tag, "html_legacy" = kramdown's default, "html_table" = Jekyll linenos),
 * optionally wrapped in HTMLLineHighlighter ("mark_lines").
 */
public final class RougeCaseRunner {
  private RougeCaseRunner() {}

  static {
    DiffRunner.IMPLS.put("rouge", RougeCaseRunner::run);
  }

  public static Map<String, Object> run(Map<String, Object> c) {
    Map<String, Object> res = new LinkedHashMap<>();
    try {
      String lang = (String) c.get("lang");
      String text = (String) c.get("text");
      Lexer lexer = Lexer.findFancy(lang, text);
      if (lexer == null) {
        lexer = new PlainText(Map.of());
      }
      String fmt = c.get("formatter") == null ? "html" : (String) c.get("formatter");
      Formatter f;
      if (fmt.equals("html_legacy")) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("css_class", "highlight");
        f = new Formatters.HTMLLegacy(o);
      } else {
        f = new Formatters.HTML();
        Object ml = c.get("mark_lines");
        if (ml != null) {
          List<Long> lines = new ArrayList<>();
          for (Object o : (List<?>) ml) {
            lines.add(((Number) o).longValue());
          }
          Map<String, Object> o = new LinkedHashMap<>();
          o.put("highlight_lines", lines);
          f = new Formatters.HTMLLineHighlighter(f, o);
        }
        if (fmt.equals("html_table")) {
          Map<String, Object> o = new LinkedHashMap<>();
          o.put("css_class", "highlight");
          o.put("gutter_class", "gutter");
          o.put("code_class", "code");
          f = new Formatters.HTMLTable(f, o);
        }
      }
      List<Tok> toks = lexer.lex(text);
      res.put("output", f.format(toks));
      res.put("tag", lexer.tag());
    } catch (RuntimeException | StackOverflowError e) {
      res.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    return res;
  }
}
