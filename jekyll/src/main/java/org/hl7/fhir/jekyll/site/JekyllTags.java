package org.hl7.fhir.jekyll.site;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Tag;
import org.hl7.fhir.jekyll.liquid.Template;
import org.hl7.fhir.jekyll.liquid.Values;
import org.hl7.fhir.jekyll.rouge.Formatter;
import org.hl7.fhir.jekyll.rouge.Formatters;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.lexers.PlainText;

/** Jekyll's Liquid tags: include (OptimizedIncludeTag + Inclusion), include_relative, highlight. */
final class JekyllTags {
  private JekyllTags() {}

  /** Raised for Jekyll's non-Liquid errors (Ruby IOError / ArgumentError) so the build fails like Jekyll's. */
  static final class JekyllError extends RuntimeException {
    JekyllError(String message) {
      super(message);
    }
  }

  // ---------------------------------------------------------------- tags/include.rb

  /** Port of Jekyll::Tags::IncludeTag / OptimizedIncludeTag (jekyll-4.4.1/lib/jekyll/tags/include.rb). */
  static class IncludeTag extends Tag {
    // VALID_SYNTAX (Ruby /x): ([\w-]+)\s*=\s*(?:"([^"\\]*(?:\\.[^"\\]*)*)"|'([^'\\]*(?:\\.[^'\\]*)*)'|([\w.-]+))
    static final String VALID = "([\\w-]+)\\s*=\\s*(?:\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"|'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'|([\\w.-]+))";
    static final Pattern VALID_SYNTAX = Pattern.compile(VALID, Pattern.UNIX_LINES);
    // VARIABLE_SYNTAX (Ruby /mx): (?<variable>[^{]*(\{\{\s*[\w\-.]+\s*(\|.*)?\}\}[^\s{}]*)+)(?<params>.*)
    static final Pattern VARIABLE_SYNTAX = Pattern.compile(
        "(?<variable>[^{]*(\\{\\{\\s*[\\w\\-.]+\\s*(\\|.*)?\\}\\}[^\\s{}]*)+)(?<params>.*)", Pattern.DOTALL | Pattern.MULTILINE | Pattern.UNIX_LINES);
    static final Pattern FULL_VALID_SYNTAX = Pattern.compile("\\A\\s*(?:" + VALID + "(?=\\s|\\z)\\s*)*\\z", Pattern.UNIX_LINES);
    static final Pattern VALID_FILENAME_CHARS = Pattern.compile("^[\\w/.\\-()+~#@]+$", Pattern.MULTILINE | Pattern.UNIX_LINES);
    static final Pattern INVALID_SEQUENCES = Pattern.compile("[./]{2,}", Pattern.UNIX_LINES);

    final String file;
    final String params;

    IncludeTag(String tagName, String markup, ParseContext pc) {
      super(tagName, markup, pc);
      String m = markup.strip();
      Matcher vm = VARIABLE_SYNTAX.matcher(m);
      if (vm.find()) { // Ruby String#match: first match anywhere
        file = vm.group("variable").strip();
        params = vm.group("params").strip();
      } else {
        String[] parts = m.split("\\s+", 2);
        file = parts[0];
        params = parts.length > 1 ? parts[1] : null;
      }
      if (params != null && !FULL_VALID_SYNTAX.matcher(params).find()) {
        throw new JekyllError("Invalid syntax for include tag:\n\n" + params + "\n\nValid syntax:\n\n" + syntaxExample() + "\n\n");
      }
    }

    String syntaxExample() {
      return "{% " + tagName() + " file.ext param='value' param2='value' %}";
    }

    Map<String, Object> parseParams(Context context) {
      Map<String, Object> out = new LinkedHashMap<>();
      Matcher m = VALID_SYNTAX.matcher(params);
      while (m.find()) {
        String key = m.group(1);
        Object value;
        if (m.group(2) != null) {
          value = m.group(2).contains("\\\"") ? m.group(2).replace("\\\"", "\"") : m.group(2);
        } else if (m.group(3) != null) {
          value = m.group(3).contains("\\'") ? m.group(3).replace("\\'", "'") : m.group(3);
        } else {
          value = context.get(m.group(4));
        }
        out.put(key, value);
      }
      return out;
    }

    String renderVariable(Context context) {
      if (VARIABLE_SYNTAX.matcher(file).find()) {
        return Template.parse(file, JekyllRenderer.parseOptions(context)).render(context);
      }
      return null;
    }

    void validateFileName(String f) {
      if (INVALID_SEQUENCES.matcher(f).find() || !VALID_FILENAME_CHARS.matcher(f).find()) {
        throw new JekyllError("Invalid syntax for include tag. File contains invalid characters or sequences:\n\n  " + f
            + "\n\nValid syntax:\n\n  " + syntaxExample() + "\n\n");
      }
    }

    List<Path> includesDirs(Context context) {
      return List.of(JekyllRenderer.site(context).source.resolve("_includes"));
    }

    @Override
    public Object render(Context context) {
      String rendered = renderVariable(context);
      String f = rendered != null ? rendered : file;
      validateFileName(f);
      Inclusion inc = locate(context, f);
      return context.stack(() -> {
        if (params != null) {
          context.set("include", parseParams(context));
        }
        return inc.render(context);
      });
    }

    Inclusion locate(Context context, String f) {
      JekyllRenderer r = JekyllRenderer.renderer(context);
      // OptimizedIncludeTag: @site.inclusions[file] ||= locate_include_file(file). Resolution is
      // shared site-wide (one slot per name, resolved once even when pages render in parallel);
      // parsed templates are cached per thread (see Inclusion).
      List<Path> dirs = includesDirs(context);
      IncludeSlot slot = r.inclusionsByName.computeIfAbsent(cacheKey(context, f), k -> new IncludeSlot());
      return slot.get(() -> {
        Site site = JekyllRenderer.site(context);
        Path includesRoot = site.source.resolve("_includes");
        // PathManager.join(dir, file) = File.join, which never lets a leading "/" make the path
        // absolute ({% include /menu.xml %} is _includes/menu.xml); Path.resolve would.
        String rel = f.replaceFirst("^/+", "");
        for (Path dir : dirs) {
          Path p = dir.resolve(rel);
          if (r.includeProvider != null && p.startsWith(includesRoot)) {
            String name = includesRoot.relativize(p).toString().replace('\\', '/');
            String content;
            try {
              content = r.includeProvider.include(name);
            } catch (Exception e) {
              throw new JekyllError("Include provider failed for '" + name + "': " + e);
            }
            if (content != null) {
              r.includesFromProvider.incrementAndGet();
              return new Inclusion(p, r, content.startsWith("\uFEFF") ? content.substring(1) : content);
            }
          }
          if (Files.isRegularFile(p)) {
            return new Inclusion(p, r, null);
          }
        }
        List<String> shown = new ArrayList<>();
        dirs.forEach(d -> shown.add(Values.inspect(d.toString())));
        throw new JekyllError("Could not locate the included file '" + f + "' in any of [" + String.join(", ", shown)
            + "]. Ensure it exists in one of those directories and, if it is a symlink, does not point outside your site source.");
      });
    }

    String cacheKey(Context context, String f) {
      return f;
    }
  }

  /** Port of Jekyll::Tags::IncludeRelativeTag: resolves against the including page's directory. */
  static final class IncludeRelativeTag extends IncludeTag {
    IncludeRelativeTag(String tagName, String markup, ParseContext pc) {
      super(tagName, markup, pc);
    }

    @Override
    List<Path> includesDirs(Context context) {
      Site site = JekyllRenderer.site(context);
      Object page = context.registers().get("page");
      if (!(page instanceof Map)) {
        return List.of(site.source);
      }
      String path = String.valueOf(((Map<?, ?>) page).get("path"));
      if (path.endsWith("/#excerpt")) {
        path = path.substring(0, path.length() - "/#excerpt".length());
      }
      Path parent = site.source.resolve(path).getParent();
      return List.of(parent == null ? site.source : parent);
    }

    @Override
    String cacheKey(Context context, String f) {
      // IncludeRelativeTag isn't the optimized tag: it relocates per page directory
      return "relative:" + includesDirs(context).get(0) + "/" + f;
    }
  }

  /**
   * site.inclusions[name]: resolved once (first use; concurrent first uses wait), then shared. A
   * failed resolution isn't kept, so each use reports it, as Jekyll's locate would.
   */
  static final class IncludeSlot {
    private volatile Inclusion inclusion;

    Inclusion get(java.util.function.Supplier<Inclusion> locate) {
      Inclusion i = inclusion;
      if (i != null) {
        return i;
      }
      synchronized (this) {
        if (inclusion == null) {
          inclusion = locate.get();
        }
        return inclusion;
      }
    }
  }

  /**
   * Port of Jekyll::Inclusion: a located partial rendered with render!(context). Memory policy (the
   * output is the same as always parsing): most publisher fragments are used by one page, so a
   * parsed template is cached (per render thread; port Templates aren't concurrency-safe) only
   * from its second use; fragments that are exactly "{% raw %}TEXT{% endraw %}" with no "{%" in
   * TEXT render to TEXT, so they're never tokenized. A file is re-read on each uncached use; content
   * from an {@link org.hl7.fhir.jekyll.IncludeProvider} is kept (it was asked for once).
   */
  static final class Inclusion {
    private static final String RAW_OPEN = "{% raw %}";
    private static final String RAW_CLOSE = "{% endraw %}";
    final Path path;
    private final JekyllRenderer renderer;
    /** Provider content (null = read the file); for a literal raw fragment, just its text. */
    private final String provided;
    private final boolean providedLiteral;
    private final java.util.concurrent.atomic.AtomicInteger uses = new java.util.concurrent.atomic.AtomicInteger();
    private final ThreadLocal<Template> cached = new ThreadLocal<>();

    Inclusion(Path path, JekyllRenderer renderer, String provided) {
      this.path = path;
      this.renderer = renderer;
      String literal = provided == null ? null : rawLiteral(provided);
      this.providedLiteral = literal != null;
      this.provided = literal != null ? literal : provided;
    }

    /** TEXT when content is exactly "{% raw %}TEXT{% endraw %}" with no "{%" in TEXT, else null. */
    private static String rawLiteral(String content) {
      if (content.length() >= RAW_OPEN.length() + RAW_CLOSE.length() && content.startsWith(RAW_OPEN) && content.endsWith(RAW_CLOSE)) {
        String inner = content.substring(RAW_OPEN.length(), content.length() - RAW_CLOSE.length());
        if (!inner.contains("{%")) {
          return inner;
        }
      }
      return null;
    }

    Object render(Context context) {
      if (providedLiteral) {
        return provided;
      }
      Template t = cached.get();
      if (t != null) {
        return t.renderStrict(context);
      }
      String content = provided != null ? provided : Site.readUtf8(path);
      if (provided == null) {
        String inner = rawLiteral(content);
        if (inner != null) {
          return inner;
        }
      }
      t = renderer.parse(content, path.toString());
      if (uses.incrementAndGet() > 1) {
        cached.set(t);
      }
      return t.renderStrict(context);
    }
  }

  // ---------------------------------------------------------------- tags/highlight.rb

  /** Port of Jekyll::Tags::HighlightBlock (jekyll-4.4.1/lib/jekyll/tags/highlight.rb), highlighting with the Rouge port. */
  static final class HighlightBlock extends Block {
    static final Pattern SYNTAX = Pattern.compile("^([a-zA-Z0-9.+#_-]+)((\\s+\\w+(=(\\w+|\"([0-9]+\\s)*[0-9]+\"))?)*)$",
        Pattern.MULTILINE | Pattern.UNIX_LINES);
    static final Pattern OPTIONS = Pattern.compile("(?:\\w=\"[^\"]*\"|\\w=\\w|\\w)+", Pattern.UNIX_LINES);
    static final Pattern EDGE_TERMINATORS = Pattern.compile("\\A(\\n|\\r)+|(\\n|\\r)+\\z", Pattern.UNIX_LINES);

    final String lang;
    final Map<String, Object> options = new LinkedHashMap<>();

    HighlightBlock(String tagName, String markup, ParseContext pc) {
      super(tagName, markup, pc);
      Matcher m = SYNTAX.matcher(markup.strip());
      if (!m.find()) {
        throw LiquidException.syntax("Syntax Error in tag 'highlight' while parsing the following markup:\n\n  " + markup
            + "\n\nValid syntax: highlight <lang> [linenos] [mark_lines=\"3 4 5\"]\n\n"
            + "See https://jekyllrb.com/docs/liquid/tags/#code-snippet-highlighting for more details.\n");
      }
      lang = m.group(1).toLowerCase(java.util.Locale.ROOT);
      String opts = m.group(2) == null ? "" : m.group(2);
      Matcher om = OPTIONS.matcher(opts);
      while (om.find()) {
        String[] kv = om.group().split("=", 2);
        Object value = kv.length > 1 ? kv[1] : Boolean.TRUE;
        if (kv.length > 1 && kv[1].contains("\"")) {
          value = List.of(kv[1].replace("\"", "").trim().split("\\s+"));
        }
        options.put(kv[0], value);
      }
      if (Boolean.TRUE.equals(options.get("linenos"))) {
        options.put("linenos", "inline");
      }
    }

    @Override
    public Object render(Context context) {
      Object prefix = context.get("highlighter_prefix");
      Object suffix = context.get("highlighter_suffix");
      String code = EDGE_TERMINATORS.matcher(Values.toS(super.render(context))).replaceAll("");
      String formatted = renderRouge(code);
      String attrs = "class=\"language-" + lang.replace('+', '-') + "\" data-lang=\"" + lang + "\"";
      String chomped = formatted.endsWith("\r\n") ? formatted.substring(0, formatted.length() - 2)
          : formatted.endsWith("\n") || formatted.endsWith("\r") ? formatted.substring(0, formatted.length() - 1) : formatted;
      return (prefix == null ? "" : Values.toS(prefix)) + "<figure class=\"highlight\"><pre><code " + attrs + ">" + chomped
          + "</code></pre></figure>" + (suffix == null ? "" : Values.toS(suffix));
    }

    /** render_rouge(code) */
    private String renderRouge(String code) {
      Formatter formatter = new Formatters.HTML();
      if (options.get("mark_lines") != null) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("highlight_lines", markLines());
        formatter = new Formatters.HTMLLineHighlighter(formatter, o);
      }
      if (options.get("linenos") != null) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("css_class", "highlight");
        o.put("gutter_class", "gutter");
        o.put("code_class", "code");
        formatter = new Formatters.HTMLTable(formatter, o);
      }
      Lexer lexer = Lexer.findFancy(lang, code);
      if (lexer == null) {
        lexer = PlainText.CLASS.newLexer();
      }
      return formatter.format(lexer.lex(code));
    }

    /** mark_lines: the quoted list as Integers (String#to_i), else a SyntaxError */
    private List<Long> markLines() {
      Object value = options.get("mark_lines");
      if (value instanceof List) {
        List<Long> out = new ArrayList<>();
        for (Object v : (List<?>) value) {
          Matcher m = LEADING_INT.matcher(String.valueOf(v));
          out.add(m.lookingAt() ? Long.parseLong(m.group().strip().replace("+", "")) : 0L);
        }
        return out;
      }
      throw LiquidException.syntax("Syntax Error for mark_lines declaration. Expected a double-quoted list of integers.");
    }

    private static final Pattern LEADING_INT = Pattern.compile("[ \\t\\n\\x0B\\f\\r]*[-+]?[0-9]+");
  }

  /** Rouge::Formatters::HTML escaping (escape_special_html_chars: & < > only). */
  static String rougeEscape(String s) {
    StringBuilder b = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&': b.append("&amp;"); break;
        case '<': b.append("&lt;"); break;
        case '>': b.append("&gt;"); break;
        default: b.append(c);
      }
    }
    return b.toString();
  }
}
