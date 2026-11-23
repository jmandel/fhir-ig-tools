package org.hl7.fhir.jekyll.kramdown;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Formatter;
import org.hl7.fhir.jekyll.rouge.Formatters;
import org.hl7.fhir.jekyll.rouge.Lexer;

/**
 * Port of kramdown-2.6.1/lib/kramdown/converter/syntax_highlighter/rouge.rb, calling the Rouge 4.7.0
 * port (org.hl7.fhir.jekyll.rouge): <code>Rouge::Lexer.find_fancy</code>, the formatter chosen by
 * <code>formatter_class(opts)</code>, and <code>formatter.format(lexer.lex(text))</code>.
 */
final class RougeHighlighter {
  private RougeHighlighter() {}

  /** The per-type options cache (converter.data[:syntax_highlighter_rouge]). */
  @SuppressWarnings("unchecked")
  static Map<Object, Object> options(HtmlConverter converter, String type) {
    Map<String, Object> cache = (Map<String, Object>) converter.data.get("syntax_highlighter_rouge");
    if (cache == null) {
      cache = new java.util.HashMap<>();
      converter.data.put("syntax_highlighter_rouge", cache);
      Map<Object, Object> opts = new java.util.LinkedHashMap<>((Map<Object, Object>) converter.options.get("syntax_highlighter_opts"));
      Object so = opts.remove("span");
      Object bo = opts.remove("block");
      Map<Object, Object> spanOpts = normalizeKeys(so);
      Map<Object, Object> blockOpts = normalizeKeys(bo);
      Map<Object, Object> span = new java.util.LinkedHashMap<>(opts);
      span.putAll(spanOpts);
      span.put("wrap", false);
      Map<Object, Object> block = new java.util.LinkedHashMap<>(opts);
      block.putAll(blockOpts);
      cache.put("span", span);
      cache.put("block", block);
    }
    return (Map<Object, Object>) cache.get(type);
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> normalizeKeys(Object o) {
    Map<Object, Object> out = new java.util.LinkedHashMap<>();
    if (o instanceof Map) {
      for (Map.Entry<Object, Object> e : ((Map<Object, Object>) o).entrySet()) {
        Object k = e.getKey() instanceof String ? Options.strToSym((String) e.getKey()) : e.getKey();
        out.put(k, e.getValue());
      }
    }
    return out;
  }

  private static boolean truthy(Object o) {
    return o != null && !Boolean.FALSE.equals(o);
  }

  /** Rouge.call(converter, text, lang, type, call_opts) */
  static String call(HtmlConverter converter, String text, String lang, String type, Map<String, Object> callOpts) {
    Map<Object, Object> opts = options(converter, type);
    callOpts.put("default_lang", opts.get("default_lang"));
    Object defaultLang = opts.get("default_lang");
    if (lang == null && !truthy(defaultLang) && !truthy(opts.get("guess_lang"))) {
      return null;
    }
    String name = lang != null ? lang : (truthy(defaultLang) ? defaultLang.toString() : null);
    Lexer lexer = Lexer.findFancy(name, text);
    if (truthy(opts.get("disable")) || lexer == null || (lexer.tag().equals("plaintext") && !truthy(opts.get("guess_lang")))) {
      return null;
    }
    if (!truthy(opts.get("css_class"))) {
      opts.put("css_class", "highlight"); // For backward compatibility when using Rouge 2.0
    }
    Map<String, Object> fopts = new LinkedHashMap<>();
    for (Map.Entry<Object, Object> e : opts.entrySet()) {
      fopts.put(String.valueOf(e.getKey()), e.getValue());
    }
    return formatterClass(fopts).format(lexer.lex(text));
  }

  private static final java.util.regex.Pattern FORMATTER_NAME = java.util.regex.Pattern.compile(
      "\\A[\\p{IsUppercase}][\\p{IsAlphabetic}\\p{Nd}_]*\\z");

  /** formatter_class(opts).new(opts) */
  private static Formatter formatterClass(Map<String, Object> opts) {
    Object formatter = opts.get("formatter");
    if (formatter instanceof String && FORMATTER_NAME.matcher((String) formatter).matches()) {
      // ::Rouge::Formatters.const_get(formatter, false), rescue NameError -> HTML
      switch ((String) formatter) {
        case "HTML": return new Formatters.HTML();
        case "HTMLLegacy": return new Formatters.HTMLLegacy(opts);
        case "HTMLTable": case "HTMLPygments": case "HTMLLinewise": case "HTMLLineHighlighter":
        case "HTMLLineTable": case "HTMLInline": case "Terminal256": case "TerminalTruecolor": case "Tex": case "Null":
          // these take other constructor arguments than (opts) or are not ported
          throw new KramdownError("Rouge formatter " + formatter + " is not supported by this port");
        default: return new Formatters.HTML();
      }
    }
    // Available in Rouge 2.0 or later
    return new Formatters.HTMLLegacy(opts);
  }

  /** Rouge::Formatters::HTML escaping (escape_special_html_chars: &amp; &lt; &gt; only). */
  static String escapeSpecialHtmlChars(String value) {
    return Formatters.HTML.escapeSpecialHtmlChars(value);
  }
}
