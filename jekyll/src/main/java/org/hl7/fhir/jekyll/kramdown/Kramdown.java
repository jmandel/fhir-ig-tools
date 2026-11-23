package org.hl7.fhir.jekyll.kramdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public entry point: converts Markdown to HTML exactly as Jekyll 4.4.1's
 * Jekyll::Converters::Markdown::KramdownParser does with kramdown 2.6.1 + kramdown-parser-gfm 1.1.0.
 *
 * <p>Ports jekyll-4.4.1/lib/jekyll/converters/markdown/kramdown_parser.rb (setup, convert and
 * Kramdown::JekyllDocument) and the "kramdown" entry of jekyll-4.4.1/lib/jekyll/configuration.rb
 * DEFAULTS. Thread-safe: every call builds its own parser and converter.
 */
public final class Kramdown {
  private Kramdown() {}

  /** Jekyll::Configuration::DEFAULTS["kramdown"] */
  static Map<String, Object> jekyllDefaults() {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("auto_ids", true);
    List<Object> levels = new ArrayList<>();
    for (long i = 1; i <= 6; i++) {
      levels.add(i);
    }
    d.put("toc_levels", levels);
    d.put("entity_output", "as_char");
    d.put("smart_quotes", "lsquo,rsquo,ldquo,rdquo");
    d.put("input", "GFM");
    d.put("hard_wrap", false);
    d.put("guess_lang", true);
    d.put("footnote_nr", 1L);
    d.put("show_warnings", false);
    return d;
  }

  /**
   * Converts {@code markdownText} with the site's {@code kramdown:} config block (string keys, values
   * as produced by YAML/JSON; may be null or empty). Equivalent to Jekyll's markdownify/converter for
   * a String input.
   */
  public static String toHtml(String markdownText, Map<String, Object> siteKramdownConfig) {
    return convert(markdownText, siteKramdownConfig, null);
  }

  /** As {@link #toHtml}, additionally collecting kramdown's parser/converter warnings. */
  @SuppressWarnings("unchecked")
  public static String convert(String markdownText, Map<String, Object> siteKramdownConfig, List<String> warningsOut) {
    if (markdownText == null) {
      markdownText = ""; // markdownify does input.to_s; nil.to_s == ""
    }
    Map<String, Object> config = setup(siteKramdownConfig);
    // Kramdown::JekyllDocument.setup: Options.merge(options)
    Map<String, Object> options = Options.merge(config);
    Object input = options.get("input");
    String parserName = input == null ? "kramdown" : input.toString();
    parserName = parserName.isEmpty() ? parserName : parserName.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
        + parserName.substring(1);
    Object[] parsed;
    switch (parserName) {
      case "GFM":
        parsed = GfmParser.parse(markdownText, options);
        break;
      case "Kramdown":
        parsed = KramdownParser.parse(markdownText, options);
        break;
      default:
        throw new KramdownError("kramdown has no parser to handle the specified input format: " + input);
    }
    Element root = (Element) parsed[0];
    Object[] out = HtmlConverter.convert(root, options);
    if (warningsOut != null) {
      warningsOut.addAll((List<String>) parsed[1]);
      warningsOut.addAll((List<String>) out[1]);
    }
    return (String) out[0];
  }

  /** Jekyll deep_merge of DEFAULTS["kramdown"] with the site block, then KramdownParser#setup. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> setup(Map<String, Object> site) {
    Map<String, Object> config = deepMerge(jekyllDefaults(), site == null ? Map.of() : site);
    // @config["syntax_highlighter"] ||= highlighter
    if (!KramdownParser.truthy(config.get("syntax_highlighter"))) {
      Object hl = config.containsKey("enable_coderay") && KramdownParser.truthy(config.get("enable_coderay"))
          ? "coderay" : "rouge";
      config.put("syntax_highlighter", hl);
    }
    Object sho = config.get("syntax_highlighter_opts");
    Map<Object, Object> shOpts = KramdownParser.truthy(sho) && sho instanceof Map
        ? new LinkedHashMap<>((Map<Object, Object>) sho) : (KramdownParser.truthy(sho) ? null : new LinkedHashMap<>());
    if (shOpts == null) {
      throw new KramdownError("syntax_highlighter_opts must be a Hash");
    }
    if (!KramdownParser.truthy(shOpts.get("default_lang"))) {
      shOpts.put("default_lang", "plaintext");
    }
    shOpts.put("guess_lang", config.get("guess_lang"));
    config.put("syntax_highlighter_opts", shOpts);
    Object coderay = config.get("coderay");
    if (!KramdownParser.truthy(coderay)) {
      config.put("coderay", new LinkedHashMap<>());
    } else if (coderay instanceof Map && !((Map<?, ?>) coderay).isEmpty()) {
      // modernize_coderay_config (legacy): opts.merge(CODERAY_DEFAULTS).merge(coderay), prefix stripped
      Map<Object, Object> m = new LinkedHashMap<>(shOpts);
      m.put("css", "style");
      m.put("bold_every", 10L);
      m.put("line_numbers", "inline");
      m.put("line_number_start", 1L);
      m.put("tab_width", 4L);
      m.put("wrap", "div");
      m.putAll((Map<Object, Object>) coderay);
      Map<Object, Object> stripped = new LinkedHashMap<>();
      for (Map.Entry<Object, Object> e : m.entrySet()) {
        String k = String.valueOf(e.getKey());
        stripped.put(k.startsWith("coderay_") ? k.substring(8) : k, e.getValue());
      }
      config.put("syntax_highlighter_opts", stripped);
    }
    return config;
  }

  /** Jekyll::Utils.deep_merge_hashes */
  @SuppressWarnings("unchecked")
  static Map<String, Object> deepMerge(Map<String, Object> master, Map<String, Object> other) {
    Map<String, Object> target = new LinkedHashMap<>(master);
    for (Map.Entry<String, Object> e : other.entrySet()) {
      Object nv = e.getValue();
      Object ov = target.get(e.getKey());
      if (nv == null && target.containsKey(e.getKey())) {
        continue;
      }
      if (ov instanceof Map && nv instanceof Map) {
        target.put(e.getKey(), deepMerge((Map<String, Object>) ov, (Map<String, Object>) nv));
      } else {
        target.put(e.getKey(), nv);
      }
    }
    return target;
  }
}
