package org.hl7.fhir.jekyll.kramdown;

import static org.hl7.fhir.jekyll.kramdown.HtmlConstants.*;
import static org.hl7.fhir.jekyll.kramdown.RubyStr.rx;
import static org.hl7.fhir.jekyll.kramdown.RubyStr.rxm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of kramdown-2.5.2/lib/kramdown/parser/kramdown.rb and the parser pieces it pulls in
 * (parser/base.rb, parser/kramdown/*.rb, the Parser module of parser/html.rb). Ruby reopens the
 * class across files; here each Ruby file is a section of this class, marked
 * {@code // ==== port of parser/kramdown/<file>.rb}.
 *
 * <p>One instance parses one document (not thread-safe itself; create one per conversion).
 * All static state (regexes, parser registry, caches of immutable patterns) is immutable or
 * concurrent.
 */
public class KramdownParser {

  // ==== port of parser/base.rb ===============================================================

  protected final String source;
  protected final Map<String, Object> options;
  protected final Element root;
  protected final List<String> warnings = new ArrayList<>();

  protected KramdownParser(String source, Map<String, Object> opts) {
    this.source = source;
    this.options = Options.merge(opts);
    Map<String, Object> ro = new HashMap<>();
    ro.put("encoding", "UTF-8");
    ro.put("location", 1);
    ro.put("options", new LinkedHashMap<String, Object>());
    ro.put("abbrev_defs", new LinkedHashMap<String, Object>());
    ro.put("abbrev_attr", new LinkedHashMap<String, Object>());
    this.root = new Element("root", null, null, ro);
    this.textType = "text";
    // kramdown.rb initialize
    resetEnv(null, null, null, "raw_text");
    updateLinkDefinitions(options.get("link_defs"));
    blockParsers = new ArrayList<>(Arrays.asList("blank_line", "codeblock", "codeblock_fenced", "blockquote",
        "atx_header", "horizontal_rule", "list", "definition_list", "block_html", "setext_header", "block_math",
        "table", "footnote_definition", "link_definition", "abbrev_definition", "block_extensions",
        "eob_marker", "paragraph"));
    spanParsers = new ArrayList<>(Arrays.asList("emphasis", "codespan", "autolink", "span_html",
        "footnote_marker", "link", "smart_quotes", "inline_math", "span_extensions", "html_entity",
        "typographic_syms", "line_break", "escaped_chars"));
  }

  /** Parser.parse(source, options) -> [root, warnings] */
  public static Object[] parse(String source, Map<String, Object> options) {
    KramdownParser p = new KramdownParser(source, options);
    p.parse();
    return new Object[] {p.root, p.warnings};
  }

  public Element root() {
    return root;
  }

  public List<String> warnings() {
    return warnings;
  }

  protected void warning(String text) {
    warnings.add(text);
  }

  private static final Pattern CRLF = rx("\\r\\n?");

  /** Base#adapt_source */
  protected String adaptSource(String src) {
    String s = src;
    if (s.indexOf('\r') >= 0) {
      s = RubyStr.gsub(s, CRLF, "\n");
    }
    s = RubyStr.chomp(s);
    return s + "\n";
  }

  /** Base#add_text(text, tree = @tree, type = @text_type) */
  protected void addText(String text) {
    addText(text, tree, textType);
  }

  protected void addText(String text, Element t) {
    addText(text, t, textType);
  }

  protected void addText(String text, Element t, String type) {
    List<Element> ch = t.children;
    Element last = ch.isEmpty() ? null : ch.get(ch.size() - 1);
    if (last != null && type != null && type.equals(last.type)) {
      last.value = ((String) last.value) + text;
    } else if (!text.isEmpty()) {
      Object location = last != null && last.opt("location") != null ? last.opt("location") : t.opt("location");
      ch.add(new Element(type, text, null, Element.opts("location", location)));
    }
  }

  // ==== port of parser/kramdown.rb ==========================================================

  /** Parser definition (Ruby Data struct: name, start_re, span_start, method). */
  protected static final class ParserDef {
    final String name;
    final Pattern startRe;
    final String spanStart;
    final ParserMethod method;
    /** Optional cheap necessary condition for start_re to match at pos (performance only). */
    volatile Prefilter prefilter;
    /** Optional: picks an equivalent faster regex for the text at pos (performance only). */
    volatile java.util.function.BiFunction<String, Integer, Pattern> chooser;

    ParserDef(String name, Pattern startRe, String spanStart, ParserMethod method) {
      this.name = name;
      this.startRe = startRe;
      this.spanStart = spanStart;
      this.method = method;
    }
  }

  /** A necessary condition for a block start regex (all of which start with ^ at the scanner pos). */
  @FunctionalInterface
  protected interface Prefilter {
    boolean mayMatch(String s, int pos);
  }

  /** index after up to 3 spaces */
  static int skip3(String s, int pos) {
    int i = pos;
    int n = s.length();
    while (i < n && i - pos < 3 && s.charAt(i) == ' ') {
      i++;
    }
    return i;
  }

  static boolean charAt(String s, int i, char c) {
    return i < s.length() && s.charAt(i) == c;
  }

  protected static void prefilter(String name, Prefilter f) {
    PARSERS.get(name).prefilter = f;
  }

  @FunctionalInterface
  protected interface ParserMethod {
    boolean call(KramdownParser p);
  }

  /** @@parsers (shared by Kramdown and GFM parsers). */
  protected static final Map<String, ParserDef> PARSERS = new ConcurrentHashMap<>();

  protected static void defineParser(String name, Pattern startRe, String spanStart, ParserMethod m) {
    if (PARSERS.containsKey(name)) {
      throw new IllegalStateException("A parser with the name " + name + " already exists!");
    }
    PARSERS.put(name, new ParserDef(name, startRe, spanStart, m));
  }

  protected List<String> blockParsers;
  protected List<String> spanParsers;
  protected Map<String, ParserDef> parsers;
  private Pattern spanStart;
  private Pattern spanStartRe;

  protected final Map<String, Map<String, Object>> alds = new HashMap<>();
  protected final Map<String, Map<String, Object>> footnotes = new LinkedHashMap<>();
  protected final Map<String, Object[]> linkDefs = new HashMap<>();

  // parser env
  protected StrScanner src;
  protected Element tree;
  protected Map<String, Object> blockIal;
  protected List<Object[]> stack;
  protected String textType;

  public void parse() {
    configureParser();
    parseBlocks(root, adaptSource(source));
    updateTree(root);
    correctAbbreviationsAttributes();
    replaceAbbreviations(root, null);
    for (Map<String, Object> data : footnotes.values()) {
      updateTree((Element) data.get("content"));
      replaceAbbreviations((Element) data.get("content"), null);
    }
    int footnoteCount = 0;
    for (Map.Entry<String, Map<String, Object>> e : footnotes.entrySet()) {
      if (e.getValue().containsKey("marker")) {
        footnoteCount++;
        continue;
      }
      Object line = ((Element) e.getValue().get("content")).opt("location");
      warning("Footnote definition for '" + e.getKey() + "' on line " + line + " is unreferenced - ignoring");
    }
    root.options().put("footnote_count", footnoteCount);
  }

  @SuppressWarnings("unchecked")
  protected void updateLinkDefinitions(Object ld) {
    if (ld instanceof Map) {
      for (Map.Entry<Object, Object> e : ((Map<Object, Object>) ld).entrySet()) {
        List<Object> v = (List<Object>) e.getValue();
        linkDefs.put(normalizeLinkId(String.valueOf(e.getKey())),
            new Object[] {v.get(0), v.size() > 1 ? v.get(1) : null, null});
      }
    }
  }

  protected void configureParser() {
    parsers = new HashMap<>();
    for (List<String> l : List.of(blockParsers, spanParsers)) {
      for (String name : l) {
        ParserDef d = PARSERS.get(name);
        if (d == null) {
          throw new KramdownError("Unknown parser: " + name);
        }
        parsers.put(name, d);
      }
    }
    Pattern[] r = spanParserRegexps(spanParsers);
    spanStart = r[0];
    spanStartRe = r[1];
  }

  private static final Map<String, Pattern[]> SPAN_RE_CACHE = new ConcurrentHashMap<>();

  protected Pattern[] spanParserRegexps(List<String> ps) {
    StringBuilder b = new StringBuilder();
    for (String name : ps) {
      if (b.length() > 0) {
        b.append('|');
      }
      b.append(parsers.get(name).spanStart);
    }
    String s = b.toString();
    return SPAN_RE_CACHE.computeIfAbsent(s, k -> new Pattern[] {rx(k), rx("(?=" + k + ")")});
  }

  /** Thrown by parse_block_html to emulate throw :stop_block_parsing, :found */
  protected static final class StopBlockParsing extends RuntimeException {
    private static final long serialVersionUID = 1L;

    StopBlockParsing() {
      super(null, null, false, false);
    }
  }

  private static final StopBlockParsing STOP = new StopBlockParsing();
  private static final Pattern ANY_LINE = rx(".*\\n");

  /** parse_blocks(el, text = nil); returns true if stopped with :found */
  protected boolean parseBlocks(Element el, String text) {
    stack.add(new Object[] {tree, src, blockIal});
    tree = el;
    blockIal = null;
    if (text != null) {
      src = new StrScanner(text, (Integer) el.opt("location"));
    }
    boolean found = false;
    try {
      while (!src.eos()) {
        boolean handled = false;
        for (String name : blockParsers) {
          ParserDef d = parsers.get(name);
          Prefilter pf = d.prefilter;
          if (pf != null && !pf.mayMatch(src.string(), src.pos())) {
            continue;
          }
          Pattern sre = d.chooser == null ? d.startRe : d.chooser.apply(src.string(), src.pos());
          if (src.check(sre) != null) {
            if (d.method.call(this)) {
              handled = true;
              break;
            }
          }
        }
        if (!handled) {
          warning("Warning: this should not occur - no block parser handled the line");
          addText(src.scan(ANY_LINE));
        }
      }
    } catch (StopBlockParsing e) {
      found = true;
    }
    Object[] f = stack.remove(stack.size() - 1);
    tree = (Element) f[0];
    src = (StrScanner) f[1];
    blockIal = castMap(f[2]);
    return found;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> castMap(Object o) {
    return (Map<String, Object>) o;
  }

  @SuppressWarnings("unchecked")
  static List<Object> castList(Object o) {
    return (List<Object>) o;
  }

  /** update_tree(element) */
  protected void updateTree(Element element) {
    Element lastBlank = null;
    List<Element> out = new ArrayList<>(element.children.size());
    for (Element child : element.children) {
      switch (child.type) {
        case "raw_text":
          lastBlank = null;
          resetEnv(new StrScanner((String) child.value, (Integer) element.opt("location")), null, null, "text");
          parseSpans(child, null, null, textType, null);
          out.addAll(child.children);
          break;
        case "eob":
          if (child.opt("ial") != null) {
            updateAttrWithIal(child.attr(), castMap(child.opt("ial")));
          }
          break;
        case "blank":
          if (lastBlank != null) {
            lastBlank.value = ((String) lastBlank.value) + child.value;
          } else {
            lastBlank = child;
            out.add(child);
          }
          break;
        default:
          lastBlank = null;
          updateTree(child);
          if (child.opt("ial") != null) {
            updateAttrWithIal(child.attr(), castMap(child.opt("ial")));
          }
          if (child.type.equals("dt") || (child.type.equals("header") && truthy(options.get("auto_id_stripping")))) {
            updateRawText(child);
          }
          out.add(child);
      }
    }
    element.children = out;
  }

  static boolean truthy(Object o) {
    return o != null && !Boolean.FALSE.equals(o);
  }

  private static final Map<String, Pattern> SPAN_PATTERN_CACHE = new ConcurrentHashMap<>();

  private Pattern spanPatternCache(Pattern stopRe, Pattern spanStartP) {
    String key = stopRe.flags() + "\u0000" + stopRe.pattern() + "\u0000" + spanStartP.pattern();
    Pattern p = SPAN_PATTERN_CACHE.get(key);
    if (p == null) {
      if (SPAN_PATTERN_CACHE.size() > 20000) {
        SPAN_PATTERN_CACHE.clear();
      }
      // Regexp.union(stop_re, span_start) => (?-mix:stop)|(?-mix:span_start)
      p = rx("(?=" + RubyStr.emb(stopRe) + "|" + RubyStr.emb(spanStartP) + ")");
      SPAN_PATTERN_CACHE.put(key, p);
    }
    return p;
  }

  /** parse_spans(el, stop_re = nil, parsers = nil, text_type = @text_type) { block } */
  protected boolean parseSpans(Element el, Pattern stopRe, List<String> ps, String tt, BooleanSupplier blk) {
    if (tree != null) {
      stack.add(new Object[] {tree, textType});
    }
    tree = el;
    textType = tt;
    Pattern sStart = spanStart;
    Pattern sStartRe = spanStartRe;
    if (ps != null) {
      Pattern[] r = spanParserRegexps(ps);
      sStart = r[0];
      sStartRe = r[1];
    } else {
      ps = spanParsers;
    }
    Pattern usedRe = stopRe == null ? sStartRe : spanPatternCache(stopRe, sStart);
    boolean stopReFound = false;
    while (!src.eos() && !stopReFound) {
      String result = src.scanUntil(usedRe);
      if (result != null) {
        addText(result);
        if (stopRe != null && src.check(stopRe) != null) {
          stopReFound = blk != null ? blk.getAsBoolean() : true;
        }
        boolean processed = false;
        if (!stopReFound) {
          for (String name : ps) {
            ParserDef d = parsers.get(name);
            if (src.check(d.startRe) != null) {
              d.method.call(this);
              processed = true;
              break;
            }
          }
        }
        if (!processed && !stopReFound) {
          addText(src.getch());
        }
      } else {
        if (stopRe == null) {
          addText(src.rest());
          src.terminate();
        }
        break;
      }
    }
    if (stack.isEmpty()) {
      tree = null;
      textType = null;
    } else {
      Object[] f = stack.remove(stack.size() - 1);
      tree = (Element) f[0];
      textType = f.length > 1 ? (String) f[1] : null;
    }
    return stopReFound;
  }

  /** reset_env(opts) */
  protected void resetEnv(StrScanner s, Element t, Map<String, Object> bi, String tt) {
    src = s;
    tree = t;
    blockIal = bi;
    stack = new ArrayList<>();
    textType = tt;
  }

  protected Object[] saveEnv() {
    return new Object[] {src, tree, blockIal, stack, textType};
  }

  @SuppressWarnings("unchecked")
  protected void restoreEnv(Object[] env) {
    src = (StrScanner) env[0];
    tree = (Element) env[1];
    blockIal = (Map<String, Object>) env[2];
    stack = (List<Object[]>) env[3];
    textType = (String) env[4];
  }

  /** update_attr_with_ial(attr, ial) */
  @SuppressWarnings("unchecked")
  protected void updateAttrWithIal(Map<String, Object> attr, Map<String, Object> ial) {
    Object refs = ial.get(Element.REFS);
    if (refs != null) {
      for (Object ref : new ArrayList<>((List<Object>) refs)) {
        Map<String, Object> r = alds.get((String) ref);
        if (r != null) {
          updateAttrWithIal(attr, r);
        }
      }
    }
    for (Map.Entry<String, Object> e : new ArrayList<>(ial.entrySet())) {
      String k = e.getKey();
      if (k.equals(IAL_CLASS_ATTR)) {
        Object cur = attr.get(k);
        attr.put(k, RubyStr.lstrip((cur == null ? "" : cur.toString()) + " " + e.getValue()));
      } else if (!k.equals(Element.REFS)) {
        attr.put(k, e.getValue());
      }
    }
  }

  /** update_raw_text(item) */
  protected void updateRawText(Element item) {
    StringBuilder raw = new StringBuilder();
    appendRaw(item, raw);
    item.options().put("raw_text", raw.toString());
  }

  private void appendRaw(Element child, StringBuilder raw) {
    if (child.type.equals("text")) {
      raw.append((String) child.value);
    } else {
      for (Element c : child.children) {
        appendRaw(c, raw);
      }
    }
  }

  /** new_block_el(*args) */
  protected Element newBlockEl(String type, Object value, Map<String, Object> attr, Map<String, Object> opts) {
    Element el = new Element(type, value, attr, opts);
    if (blockIal != null) {
      el.options().put("ial", blockIal);
      blockIal = null;
    }
    return el;
  }

  // shared regex fragments (each wrapped so it can be embedded like Ruby's #{re})
  static final String OPT_SPACE = "(?: {0,3})";
  static final String INDENT_S = "(?:^(?:\\t| {4}))";
  static final Pattern INDENT = rx("^(?:\\t| {4})");

  // ==== port of parser/kramdown/blank_line.rb ===============================================
  static final String BLANK_LINE_S = "(?:(?>^\\s*\\n)+)";
  static final Pattern BLANK_LINE = rx("(?>^\\s*\\n)+");

  protected boolean parseBlankLine() {
    src.addPos(src.matchedSize());
    String m = src.matched();
    List<Element> ch = tree.children;
    Element last = ch.isEmpty() ? null : ch.get(ch.size() - 1);
    if (last != null && last.type.equals("blank")) {
      last.value = ((String) last.value) + m;
    } else {
      ch.add(newBlockEl("blank", m, null, null));
    }
    return true;
  }

  // ==== port of parser/kramdown/eob.rb ======================================================
  static final String EOB_MARKER_S = "(?:^\\^\\s*?\\n)";
  static final Pattern EOB_MARKER = rx("^\\^\\s*?\\n");

  protected boolean parseEobMarker() {
    src.addPos(src.matchedSize());
    tree.children.add(newBlockEl("eob", null, null, null));
    return true;
  }

  // ==== port of parser/kramdown/extensions.rb (constants first, used by others) =============
  static final String IAL_CLASS_ATTR = "class";
  static final String ALD_ID_CHARS = "(?:[\\w-])";
  static final String ALD_ANY_CHARS = "(?:\\\\\\}|[^}])";
  static final String ALD_ID_NAME = "(?:\\w" + ALD_ID_CHARS + "*)";
  static final String ALD_CLASS_NAME = "(?:[^\\s.#]+)";
  static final String ALD_TYPE_KEY_VALUE_PAIR = "(?:(" + ALD_ID_NAME + ")=(\"|')((?:\\\\\\}|\\\\\\2|[^}\\x02])*?)\\2)";
  static final String ALD_TYPE_CLASS_NAME = "(?:\\.(" + ALD_CLASS_NAME + "))";
  static final String ALD_TYPE_ID_NAME = "(?:#([A-Za-z][\\w:-]*))";
  static final Pattern ALD_TYPE_ID_OR_CLASS = rx(ALD_TYPE_ID_NAME + "|" + ALD_TYPE_CLASS_NAME);
  static final String ALD_TYPE_ID_OR_CLASS_MULTI = "(?:((?:" + ALD_TYPE_ID_NAME + "|" + ALD_TYPE_CLASS_NAME + ")+))";
  static final String ALD_TYPE_REF = "(?:(" + ALD_ID_NAME + "))";
  static final Pattern ALD_TYPE_ANY = rx("(?:\\A|\\s)(?:" + ALD_TYPE_KEY_VALUE_PAIR + "|" + ALD_TYPE_REF + "|"
      + ALD_TYPE_ID_OR_CLASS_MULTI + ")(?=\\s|\\Z)");
  static final Pattern ALD_START = rx("^" + OPT_SPACE + "\\{:(" + ALD_ID_NAME + "):(" + ALD_ANY_CHARS + "+)\\}\\s*?\\n");
  static final String EXT_STOP_STR = "\\{:/(%s)?\\}";
  static final String EXT_START_STR = "\\{::(\\w+)(?:\\s(" + ALD_ANY_CHARS + "*?)|)(\\/)?\\}";
  static final Pattern EXT_BLOCK_START = rx("^" + OPT_SPACE + "(?:" + EXT_START_STR + "|"
      + String.format(EXT_STOP_STR, ALD_ID_NAME) + ")\\s*?\\n");
  static final String EXT_BLOCK_STOP_STR = "^" + OPT_SPACE + EXT_STOP_STR + " *?\n";
  static final String IAL_BLOCK_S = "(?:\\{:(?!:|\\/)(" + ALD_ANY_CHARS + "+)\\}\\s*?\\n)";
  static final String IAL_BLOCK_START_S = "(?:^" + OPT_SPACE + IAL_BLOCK_S + ")";
  static final Pattern IAL_BLOCK_START = rx("^" + OPT_SPACE + IAL_BLOCK_S);
  static final Pattern BLOCK_EXTENSIONS_START = rx("^" + OPT_SPACE + "\\{:");
  static final Pattern EXT_SPAN_START = rx(EXT_START_STR + "|" + String.format(EXT_STOP_STR, ALD_ID_NAME));
  static final Pattern IAL_SPAN_START = rx("\\{:(" + ALD_ANY_CHARS + "+)\\}");
  static final Pattern SPAN_EXTENSIONS_START = rx("\\{:");

  private static final Map<String, Pattern> SEP_UNESCAPE = Map.of(
      "\"", rx("\\\\(\\}|\")"), "'", rx("\\\\(\\}|')"));

  /** parse_attribute_list(str, opts) */
  @SuppressWarnings("unchecked")
  protected void parseAttributeList(String str, Map<String, Object> opts) {
    String st = RubyStr.strip(str);
    if (st.isEmpty() || st.equals(":")) {
      return;
    }
    Matcher m = ALD_TYPE_ANY.matcher(str);
    boolean any = false;
    while (m.find()) {
      any = true;
      String key = m.group(1);
      String sep = m.group(2);
      String val = m.group(3);
      String ref = m.group(4);
      String idAndOrClass = m.group(5);
      if (ref != null) {
        List<Object> refs = (List<Object>) opts.get(Element.REFS);
        if (refs == null) {
          refs = new ArrayList<>();
          opts.put(Element.REFS, refs);
        }
        refs.add(ref);
      } else if (idAndOrClass != null) {
        Matcher m2 = ALD_TYPE_ID_OR_CLASS.matcher(idAndOrClass);
        while (m2.find()) {
          String idAttr = m2.group(1);
          String classAttr = m2.group(2);
          if (classAttr != null) {
            Object cur = opts.get(IAL_CLASS_ATTR);
            opts.put(IAL_CLASS_ATTR, RubyStr.lstrip((cur == null ? "" : cur.toString()) + " " + classAttr));
          } else {
            opts.put("id", idAttr);
          }
        }
      } else {
        val = RubyStr.gsub(val, SEP_UNESCAPE.get(sep), "\\1");
        opts.put(key, val);
      }
    }
    if (!any) {
      warning("No or invalid attributes found in IAL/ALD content: " + str);
    }
  }

  /** update_ial_with_ial(ial, opts) */
  @SuppressWarnings("unchecked")
  protected void updateIalWithIal(Map<String, Object> ial, Map<String, Object> opts) {
    if (opts.containsKey(Element.REFS)) {
      List<Object> refs = (List<Object>) ial.get(Element.REFS);
      if (refs == null) {
        refs = new ArrayList<>();
        ial.put(Element.REFS, refs);
      }
      refs.addAll((List<Object>) opts.get(Element.REFS));
    }
    for (Map.Entry<String, Object> e : opts.entrySet()) {
      String k = e.getKey();
      if (k.equals(IAL_CLASS_ATTR)) {
        Object cur = ial.get(k);
        ial.put(k, RubyStr.lstrip((cur == null ? "" : cur.toString()) + " " + e.getValue()));
      } else if (!k.equals(Element.REFS)) {
        ial.put(k, e.getValue());
      }
    }
  }

  private static final Map<String, Pattern> EXT_STOP_CACHE = new ConcurrentHashMap<>();

  /** parse_extension_start_tag(type) */
  protected boolean parseExtensionStartTag(String type) {
    int[] savedPos = src.savePos();
    int startLineNumber = src.currentLineNumber();
    String g1 = src.group(1);
    String g2 = src.group(2);
    String g3 = src.group(3);
    String g4 = src.group(4);
    String matched = src.matched();
    src.addPos(src.matchedSize());
    if (g4 != null || matched.equals("{:/}")) {
      String name = g4 != null ? "for '" + g4 + "' " : "";
      return errorBlock(type, savedPos, "Invalid extension stop tag " + name + "found on line " + startLineNumber
          + " - ignoring it");
    }
    String ext = g1;
    Map<String, Object> opts = new LinkedHashMap<>();
    String body = null;
    parseAttributeList(g2 == null ? "" : g2, opts);
    if (g3 == null) {
      String key = type + ":" + ext;
      Pattern stopRe = EXT_STOP_CACHE.computeIfAbsent(key, k -> type.equals("block")
          ? rx(String.format(EXT_BLOCK_STOP_STR, ext)) : rx(String.format(EXT_STOP_STR, ext)));
      String result = src.scanUntil(stopRe);
      if (result != null) {
        Matcher m = stopRe.matcher(result);
        m.find();
        body = result.substring(0, m.start()) + result.substring(m.end());
        if (type.equals("block")) {
          body = RubyStr.chomp(body);
        }
      } else {
        return errorBlock(type, savedPos, "No stop tag for extension '" + ext + "' found on line "
            + startLineNumber + " - ignoring it");
      }
    }
    if (handleExtension(ext, opts, body, type, startLineNumber)) {
      return true;
    } else {
      return errorBlock(type, savedPos, "Invalid extension with name '" + ext + "' specified on line "
          + startLineNumber + " - ignoring it");
    }
  }

  private boolean errorBlock(String type, int[] savedPos, String msg) {
    warning(msg);
    src.revertPos(savedPos);
    if (type.equals("span")) {
      addText(src.getch());
    }
    return false;
  }

  private static final Pattern WS_PLUS = rx("\\s+");

  /** handle_extension(name, opts, body, type, line_no) */
  @SuppressWarnings("unchecked")
  protected boolean handleExtension(String name, Map<String, Object> opts, String body, String type, int lineNo) {
    switch (name) {
      case "comment":
        if (body != null) {
          tree.children.add(new Element("comment", body, null, Element.opts("category", type, "location", lineNo)));
        }
        return true;
      case "nomarkdown":
        if (body != null) {
          Object t = opts.get("type");
          List<String> types = RubyStr.split(t == null ? "" : t.toString(), WS_PLUS, 0);
          tree.children.add(new Element("raw", body, null,
              Element.opts("category", type, "location", lineNo, "type", types)));
        }
        return true;
      case "options":
        for (Map.Entry<String, Object> e : opts.entrySet()) {
          String k = e.getKey();
          if (Options.isDefined(k)) {
            List<Object> forbidden = (List<Object>) options.get("forbidden_inline_options");
            if (forbidden.contains(k) || k.equals("forbidden_inline_options")) {
              warning("Option " + k + " may not be set inline");
              continue;
            }
            try {
              Object val = Options.parse(k, e.getValue());
              options.put(k, val);
              castMap(root.opt("options")).put(k, val);
            } catch (RuntimeException ex) {
              // rescue StandardError
            }
          } else {
            warning("Unknown kramdown option '" + k + "'");
          }
        }
        if (type.equals("block")) {
          tree.children.add(newBlockEl("eob", "extension", null, null));
        }
        return true;
      default:
        return false;
    }
  }

  /** parse_block_extensions */
  protected boolean parseBlockExtensions() {
    if (src.scan(ALD_START) != null) {
      String id = src.group(1);
      String content = src.group(2);
      Map<String, Object> ald = alds.computeIfAbsent(id, k -> new LinkedHashMap<>());
      parseAttributeList(content, ald);
      tree.children.add(newBlockEl("eob", "ald", null, null));
      return true;
    } else if (src.check(EXT_BLOCK_START) != null) {
      return parseExtensionStartTag("block");
    } else if (src.scan(IAL_BLOCK_START) != null) {
      String content = src.group(1);
      List<Element> ch = tree.children;
      Element last = ch.isEmpty() ? null : ch.get(ch.size() - 1);
      if (last != null && !last.type.equals("blank") && (!last.type.equals("eob")
          || "link_def".equals(last.value) || "abbrev_def".equals(last.value) || "footnote_def".equals(last.value))) {
        parseAttributeList(content, ialOf(last));
        if (src.check(IAL_BLOCK_START) == null) {
          tree.children.add(newBlockEl("eob", "ial", null, null));
        }
      } else {
        if (blockIal == null) {
          blockIal = new LinkedHashMap<>();
        }
        parseAttributeList(content, blockIal);
      }
      return true;
    } else {
      return false;
    }
  }

  /** el.options[:ial] ||= {} */
  protected static Map<String, Object> ialOf(Element el) {
    Map<String, Object> ial = castMap(el.opt("ial"));
    if (ial == null) {
      ial = new LinkedHashMap<>();
      el.options().put("ial", ial);
    }
    return ial;
  }

  /** parse_span_extensions */
  protected boolean parseSpanExtensions() {
    if (src.check(EXT_SPAN_START) != null) {
      parseExtensionStartTag("span");
    } else if (src.check(IAL_SPAN_START) != null) {
      List<Element> ch = tree.children;
      Element last = ch.isEmpty() ? null : ch.get(ch.size() - 1);
      if (last != null && !last.type.equals("text")) {
        String content = src.group(1);
        src.addPos(src.matchedSize());
        Map<String, Object> attr = new LinkedHashMap<>();
        parseAttributeList(content, attr);
        updateIalWithIal(ialOf(last), attr);
        updateAttrWithIal(last.attr(), attr);
      } else {
        warning("Found span IAL after text - ignoring it");
        addText(src.getch());
      }
    } else {
      addText(src.getch());
    }
    return true;
  }

  // ==== port of parser/kramdown/paragraph.rb (+ block_boundary.rb) ===========================
  static final List<String> LAZY_END_HTML_SPAN_ELEMENTS;
  static final String LAZY_END_HTML_START_S;
  static final String LAZY_END_HTML_STOP_S;

  static {
    List<String> l = new ArrayList<>(HTML_SPAN_ELEMENTS);
    l.add("script");
    LAZY_END_HTML_SPAN_ELEMENTS = l;
    String alt = String.join("|", l);
    LAZY_END_HTML_START_S = "(?:<(?>(?!(?:" + alt + ")" + WB + ")" + UNAME_STR + "))";
    LAZY_END_HTML_STOP_S = "(?:<\\/(?!(?:" + alt + ")" + WB + ")" + UNAME_STR + "\\s*>)";
  }

  static final String LAZY_END_S = "(?:" + BLANK_LINE_S + "|" + IAL_BLOCK_START_S + "|" + EOB_MARKER_S + "|^"
      + OPT_SPACE + LAZY_END_HTML_STOP_S + "|^" + OPT_SPACE + LAZY_END_HTML_START_S + "|\\Z)";
  static final Pattern LAZY_END = rx(LAZY_END_S);
  static final Pattern PARAGRAPH_START = rx("^" + OPT_SPACE + "[^ \\t].*?\\n");
  static final Pattern PARAGRAPH_MATCH = rx("^.*?\\n");

  // list.rb constants used by paragraph end
  static final String PATTERN_TAIL = "(?:[\\t| ].*?\\n)";
  static final String DEFINITION_LIST_START_S = "(?:^(" + OPT_SPACE + ":)(" + PATTERN_TAIL + "))";
  static final Pattern DEFINITION_LIST_START = rx(DEFINITION_LIST_START_S);
  static final Pattern PARAGRAPH_END = rx(LAZY_END_S + "|" + DEFINITION_LIST_START_S);

  protected Pattern paragraphEnd() {
    return PARAGRAPH_END;
  }

  protected Pattern lazyEnd() {
    return LAZY_END;
  }

  protected boolean parseParagraph() {
    int pos = src.pos();
    int startLineNumber = src.currentLineNumber();
    StringBuilder result = new StringBuilder(src.scan(PARAGRAPH_MATCH));
    Pattern pe = paragraphEnd();
    while (!src.matches(pe)) {
      String l = src.scan(PARAGRAPH_MATCH);
      if (l == null) {
        throw new IllegalStateException("no implicit conversion of nil into String");
      }
      result.append(l);
    }
    String res = RubyStr.rstrip(result.toString());
    List<Element> ch = tree.children;
    Element last = ch.isEmpty() ? null : ch.get(ch.size() - 1);
    if (last != null && last.type.equals("p")) {
      Element lastItem = last.children.isEmpty() ? null : last.children.get(last.children.size() - 1);
      if (lastItem != null && lastItem.type.equals(textType)) {
        String joiner = "  \n".equals(src.extract(pos - 3, pos)) ? "  \n" : "\n";
        lastItem.value = ((String) lastItem.value) + joiner + res;
      } else {
        addText(res, last);
      }
    } else {
      Element p = newBlockEl("p", null, null, Element.opts("location", startLineNumber));
      tree.children.add(p);
      res = RubyStr.lstrip(res);
      addText(res, p);
    }
    return true;
  }

  static final Pattern BLOCK_BOUNDARY = rx(BLANK_LINE_S + "|" + EOB_MARKER_S + "|" + IAL_BLOCK_START_S + "|\\Z");

  protected boolean afterBlockBoundary() {
    List<Element> ch = tree.children;
    Element last = ch.isEmpty() ? null : ch.get(ch.size() - 1);
    return last == null || last.type.equals("blank") || (last.type.equals("eob") && last.value == null)
        || blockIal != null;
  }

  protected boolean beforeBlockBoundary() {
    return src.check(BLOCK_BOUNDARY) != null;
  }

  // ==== port of parser/kramdown/header.rb ===================================================
  static final Pattern SETEXT_HEADER_START = rx("^" + OPT_SPACE
      + "(?<contents>[^ \\t].*)\\n(?<level>[-=])[-=]*[ \\t\\r\\f\\x0B]*\\n");
  static final Pattern ATX_HEADER_START = rx("^(?<level>#{1,6})[\\t ]*(?<contents>[^ \\t].*)\\n");
  static final Pattern HEADER_ID = rx("[\\t ]\\{#(?<id>" + NAME_START_CHAR + NAME_CHAR + "*)\\}\\z");
  private static final Pattern ATX_TRAILING_HASHES = rx("(?<!\\\\)#+\\z");

  protected boolean parseSetextHeader() {
    if (!afterBlockBoundary()) {
      return false;
    }
    String[] ti = parseHeaderContents();
    if (ti[0].isEmpty()) {
      return false;
    }
    addHeader("-".equals(src.group("level")) ? 2 : 1, ti[0], ti[1]);
    return true;
  }

  protected boolean parseAtxHeader() {
    if (!afterBlockBoundary()) {
      return false;
    }
    String[] ti = parseHeaderContents();
    String text = ti[0];
    Matcher m = ATX_TRAILING_HASHES.matcher(text);
    if (m.find()) {
      text = RubyStr.rstrip(text.substring(0, m.start()) + text.substring(m.end()));
    }
    if (text.isEmpty()) {
      return false;
    }
    addHeader(src.group("level").length(), text, ti[1]);
    return true;
  }

  /** parse_header_contents -> [text, id] */
  protected String[] parseHeaderContents() {
    String text = RubyStr.rstrip(src.group("contents"));
    String id = null;
    Matcher m = HEADER_ID.matcher(text);
    if (m.find()) {
      id = m.group("id");
      text = RubyStr.rstrip(text.substring(0, text.length() - m.group().length()));
    }
    return new String[] {text, id};
  }

  protected void addHeader(int level, String text, String id) {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    Element el = newBlockEl("header", null, null,
        Element.opts("level", level, "raw_text", text, "location", startLineNumber));
    addText(text, el);
    if (id != null) {
      el.attr().put("id", id);
    }
    tree.children.add(el);
  }

  // ==== port of parser/kramdown/blockquote.rb ===============================================
  static final Pattern BLOCKQUOTE_START = rx("^" + OPT_SPACE + "> ?");

  protected boolean parseBlockquote() {
    int startLineNumber = src.currentLineNumber();
    StringBuilder result = new StringBuilder(src.scan(PARAGRAPH_MATCH));
    Pattern le = lazyEnd();
    while (!src.matches(le)) {
      result.append(src.scan(PARAGRAPH_MATCH));
    }
    String res = RubyStr.gsub(result.toString(), BLOCKQUOTE_START, "");
    Element el = newBlockEl("blockquote", null, null, Element.opts("location", startLineNumber));
    tree.children.add(el);
    parseBlocks(el, res);
    return true;
  }

  // ==== port of parser/kramdown/codeblock.rb ================================================
  static final String CODEBLOCK_MATCH_S = "(?:(?:" + BLANK_LINE_S + "?(?:" + INDENT_S + "[ \\t]*\\S.*\\n)+(?:(?!"
      + IAL_BLOCK_START_S + "|" + EOB_MARKER_S + "|^" + OPT_SPACE + LAZY_END_HTML_STOP_S + "|^" + OPT_SPACE
      + LAZY_END_HTML_START_S + ")^[ \\t]*\\S.*\\n)*)*)";
  static final Pattern CODEBLOCK_MATCH = rx(CODEBLOCK_MATCH_S);
  private static final Pattern CODEBLOCK_LAZY_JOIN = rx("\\n( {0,3}\\S)");

  protected boolean parseCodeblock() {
    int startLineNumber = src.currentLineNumber();
    String data = src.scan(CODEBLOCK_MATCH);
    data = RubyStr.gsub(data, CODEBLOCK_LAZY_JOIN, " \\1");
    data = RubyStr.gsub(data, INDENT, "");
    tree.children.add(newBlockEl("codeblock", data, null, Element.opts("location", startLineNumber)));
    return true;
  }

  static final Pattern FENCED_CODEBLOCK_START = rx("^~{3,}");
  static final Pattern FENCED_CODEBLOCK_MATCH = rxm("^((~){3,})\\s*?((\\S+?)(?:\\?\\S*)?)?\\s*?\\n(.*?)^\\1\\2*\\s*?\\n");

  protected Pattern fencedCodeblockMatch() {
    return FENCED_CODEBLOCK_MATCH;
  }

  protected boolean parseCodeblockFenced() {
    if (src.check(fencedCodeblockMatch()) != null) {
      int startLineNumber = src.currentLineNumber();
      src.addPos(src.matchedSize());
      Element el = newBlockEl("codeblock", src.group(5), null,
          Element.opts("location", startLineNumber, "fenced", true));
      String g3 = src.group(3);
      String lang = RubyStr.strip(g3 == null ? "" : g3);
      if (!lang.isEmpty()) {
        el.options().put("lang", lang);
        el.attr().put("class", "language-" + src.group(4));
      }
      tree.children.add(el);
      return true;
    } else {
      return false;
    }
  }

  // ==== port of parser/kramdown/horizontal_rule.rb ==========================================
  static final Pattern HR_START = rx("^" + OPT_SPACE + "(\\*|-|_)[ \\t]*\\1[ \\t]*\\1(\\1|[ \\t])*\\n");

  protected boolean parseHorizontalRule() {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    tree.children.add(newBlockEl("hr", null, null, Element.opts("location", startLineNumber)));
    return true;
  }

  // ==== port of parser/kramdown/list.rb =====================================================
  static final Pattern LIST_ITEM_IAL = rx("^\\s*(?:\\{:(?!(?:" + ALD_ID_NAME + ")?:|\\/)(" + ALD_ANY_CHARS
      + "+)\\})\\s*");
  static final Pattern LIST_ITEM_IAL_CHECK = rx("^(?:^\\s*(?:\\{:(?!(?:" + ALD_ID_NAME + ")?:|\\/)("
      + ALD_ANY_CHARS + "+)\\})\\s*)?\\s*\\n");

  private static final Map<Integer, Pattern[]> FIRST_LINE_CACHE = new ConcurrentHashMap<>();

  private static Pattern[] firstListLinePatterns(int indentation) {
    return FIRST_LINE_CACHE.computeIfAbsent(indentation, ind -> {
      Pattern indentRe = rx("^ {" + ind + "}");
      Pattern contentRe = rx("^(?:(?:\\t| {4}){" + (ind / 4) + "} {" + (ind % 4) + "}|(?:\\t| {4}){" + (ind / 4 + 1)
          + "}).*\\S.*\\n");
      Pattern lazyRe = rx("(?!^ {0," + Math.min(ind, 3) + "}(?:" + IAL_BLOCK_S + "|" + LAZY_END_HTML_STOP_S + "|"
          + LAZY_END_HTML_START_S + ")).*\\S.*\\n");
      return new Pattern[] {contentRe, lazyRe, indentRe};
    });
  }

  private static final Pattern SPACES_TAB = rx("^ *\\t");
  private static final Pattern LEADING_SPACES = rx("^ *");
  private static final Pattern SPACES_TABS = rx("^( *)(\\t+)");
  private static final Pattern LEADING_WS = rx("^\\s*");
  private static final Pattern LEADING_TABS = rx("^(\\t+)");

  /** parse_first_list_line(indentation, content) -> [content, indentation, content_re, lazy_re, indent_re] */
  protected Object[] parseFirstListLine(int indentation, String content) {
    if (LIST_ITEM_IAL_CHECK.matcher(content).lookingAt()) {
      indentation = 4;
    } else {
      while (SPACES_TAB.matcher(content).find()) {
        Matcher m0 = LEADING_SPACES.matcher(content);
        m0.find();
        int temp = m0.group().length() + indentation;
        final int t = temp;
        content = RubyStr.sub(content, SPACES_TABS,
            m -> m.group(1) + RubyStr.spaces(4 - (t % 4) + (m.group(2).length() - 1) * 4));
      }
      Matcher m1 = LEADING_SPACES.matcher(content);
      m1.find();
      indentation += m1.group().length();
    }
    content = RubyStr.sub(content, LEADING_WS, "");
    Pattern[] p = firstListLinePatterns(indentation);
    return new Object[] {content, indentation, p[0], p[1], p[2]};
  }

  static final String LIST_START_UL_S = "(?:^(" + OPT_SPACE + "[+*-])(" + PATTERN_TAIL + "))";
  static final String LIST_START_OL_S = "(?:^(" + OPT_SPACE + "\\d+\\.)(" + PATTERN_TAIL + "))";
  static final Pattern LIST_START_UL = rx(LIST_START_UL_S);
  static final Pattern LIST_START_OL = rx(LIST_START_OL_S);
  static final String LIST_START_S = "(?:" + LIST_START_UL_S + "|" + LIST_START_OL_S + ")";
  static final Pattern LIST_START = rx(LIST_START_S);

  @SuppressWarnings("unchecked")
  protected boolean parseList() {
    int startLineNumber = src.currentLineNumber();
    String type;
    Pattern listStartRe;
    if (src.check(LIST_START_UL) != null) {
      type = "ul";
      listStartRe = LIST_START_UL;
    } else {
      type = "ol";
      listStartRe = LIST_START_OL;
    }
    Element list = newBlockEl(type, null, null, Element.opts("location", startLineNumber));
    Element item = null;
    Pattern contentRe = null;
    Pattern lazyRe = null;
    Pattern indentRe = null;
    boolean eobFound = false;
    boolean nestedListFound = false;
    boolean lastIsBlank = false;
    int indentation = 0;
    while (!src.eos()) {
      startLineNumber = src.currentLineNumber();
      String result;
      if (lastIsBlank && src.check(HR_START) != null) {
        break;
      } else if (src.scan(EOB_MARKER) != null) {
        eobFound = true;
        break;
      } else if (src.scan(listStartRe) != null) {
        if (list.opt("first_list_marker") == null) {
          list.options().put("first_list_marker", RubyStr.strip(src.group(1)));
        }
        item = new Element("li", null, null, Element.opts("location", startLineNumber));
        Object[] r = parseFirstListLine(src.group(1).length(), src.group(2));
        String val = (String) r[0];
        indentation = (Integer) r[1];
        contentRe = (Pattern) r[2];
        lazyRe = (Pattern) r[3];
        indentRe = (Pattern) r[4];
        list.children.add(item);
        final Element it = item;
        Matcher m = LIST_ITEM_IAL.matcher(val);
        if (m.find()) {
          parseAttributeList(m.group(1), ialOf(it));
          val = val.substring(0, m.start()) + val.substring(m.end());
        }
        listStartRe = fetchPattern(type, indentation);
        nestedListFound = LIST_START.matcher(val).find();
        lastIsBlank = false;
        List<String> v = new ArrayList<>();
        v.add(val);
        item.value = v;
      } else if ((result = src.scan(contentRe)) != null || (!lastIsBlank && (result = src.scan(lazyRe)) != null)) {
        result = RubyStr.sub(result, LEADING_TABS, m -> RubyStr.spaces(4 * m.group(1).length()));
        Matcher im = indentRe.matcher(result);
        boolean indentationFound = im.find();
        if (indentationFound) {
          result = result.substring(0, im.start()) + result.substring(im.end());
        }
        List<String> v = (List<String>) item.value;
        if (!nestedListFound && indentationFound && LIST_START.matcher(result).find()) {
          v.add("");
          nestedListFound = true;
        } else if (nestedListFound && !indentationFound && LIST_START.matcher(result).find()) {
          result = RubyStr.spaces(indentation + 4) + result;
        }
        v.set(v.size() - 1, v.get(v.size() - 1) + result);
        lastIsBlank = false;
      } else if ((result = src.scan(BLANK_LINE)) != null) {
        nestedListFound = true;
        lastIsBlank = true;
        List<String> v = (List<String>) item.value;
        v.set(v.size() - 1, v.get(v.size() - 1) + result);
      } else {
        break;
      }
    }

    tree.children.add(list);

    Element last = null;
    for (Element it : list.children) {
      Element temp = new Element("temp", null, null, Element.opts("location", it.opt("location")));
      Object[] env = saveEnv();
      Integer location = (Integer) it.opt("location");
      for (String val : (List<String>) it.value) {
        src = new StrScanner(val, location);
        parseBlocks(temp, null);
        location = src.currentLineNumber();
      }
      restoreEnv(env);
      it.children = temp.children;
      it.value = null;

      List<Element> itc = it.children;
      if (itc.isEmpty()) {
        continue;
      }

      if (itc.get(0).type.equals("p") && itc.size() >= 2 && itc.get(1).type.equals("eob")
          && itc.get(0).opt("ial") != null) {
        itc.remove(1);
      }

      if (itc.get(0).type.equals("p")
          && (itc.size() < 2 || !itc.get(1).type.equals("blank")
              || (it == list.children.get(list.children.size() - 1) && itc.size() == 2 && !eobFound))
          && (list.children.get(list.children.size() - 1) != it || list.children.size() == 1
              || anyNonPara(list.children.subList(0, list.children.size() - 1)))) {
        if (itc.size() > 1 && !itc.get(1).type.equals("blank")) {
          Element f = itc.get(0).children.get(0);
          f.value = ((String) f.value) + "\n";
        }
        itc.get(0).options().put("transparent", true);
      }

      last = itc.get(itc.size() - 1).type.equals("blank") ? itc.remove(itc.size() - 1) : null;
    }

    if (last != null && !eobFound) {
      tree.children.add(last);
    }
    return true;
  }

  private static boolean anyNonPara(List<Element> items) {
    for (Element cit : items) {
      if (cit.children.isEmpty() || !cit.children.get(0).type.equals("p")
          || truthy(cit.children.get(0).opt("transparent"))) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  protected boolean parseDefinitionList() {
    List<Element> children = tree.children;
    int n = children.size();
    if (n == 0 || (n == 1 && !children.get(n - 1).type.equals("p"))
        || (n >= 2 && !children.get(n - 1).type.equals("p")
            && (!children.get(n - 1).type.equals("blank") || !"\n".equals(children.get(n - 1).value)
                || !children.get(n - 2).type.equals("p")))) {
      return false;
    }

    boolean firstAsPara = false;
    Element deflist = newBlockEl("dl", null, null, null);
    Element para = tree.children.remove(tree.children.size() - 1);
    if (para.type.equals("blank")) {
      para = tree.children.remove(tree.children.size() - 1);
      firstAsPara = true;
    }
    // TODO: location
    deflist.options().put("location", para.opt("location"));
    for (String term0 : RubyStr.split((String) para.children.get(0).value, "\n", 0)) {
      Element el = new Element("dt", null, null, Element.opts("location", src.currentLineNumber()));
      String term = term0;
      Matcher m = LIST_ITEM_IAL.matcher(term);
      if (m.find()) {
        parseAttributeList(m.group(1), ialOf(el));
        term = term.substring(0, m.start()) + term.substring(m.end());
      }
      el.options().put("raw_text", term);
      el.children.add(new Element("raw_text", term));
      deflist.children.add(el);
    }
    deflist.options().put("ial", para.opt("ial"));

    Element item = null;
    Pattern contentRe = null;
    Pattern lazyRe = null;
    Pattern indentRe = null;
    Pattern defStartRe = DEFINITION_LIST_START;
    boolean lastIsBlank = false;
    while (!src.eos()) {
      int startLineNumber = src.currentLineNumber();
      String result;
      if (src.scan(defStartRe) != null) {
        item = new Element("dd", null, null, Element.opts("location", startLineNumber));
        item.options().put("first_as_para", firstAsPara);
        Object[] r = parseFirstListLine(src.group(1).length(), src.group(2));
        String val = (String) r[0];
        int indentation = (Integer) r[1];
        contentRe = (Pattern) r[2];
        lazyRe = (Pattern) r[3];
        indentRe = (Pattern) r[4];
        deflist.children.add(item);
        Matcher m = LIST_ITEM_IAL.matcher(val);
        if (m.find()) {
          parseAttributeList(m.group(1), ialOf(item));
          val = val.substring(0, m.start()) + val.substring(m.end());
        }
        item.value = val;
        defStartRe = fetchPattern("dl", indentation);
        firstAsPara = false;
        lastIsBlank = false;
      } else if (src.check(EOB_MARKER) != null) {
        break;
      } else if ((result = src.scan(contentRe)) != null || (!lastIsBlank && (result = src.scan(lazyRe)) != null)) {
        result = RubyStr.sub(result, LEADING_TABS, m -> RubyStr.spaces(4 * m.group(1).length()));
        result = RubyStr.sub(result, indentRe, "");
        item.value = ((String) item.value) + result;
        firstAsPara = false;
        lastIsBlank = false;
      } else if ((result = src.scan(BLANK_LINE)) != null) {
        firstAsPara = true;
        item.value = ((String) item.value) + result;
        lastIsBlank = true;
      } else {
        break;
      }
    }

    Element last = null;
    for (Element it : deflist.children) {
      if (it.type.equals("dt")) {
        continue;
      }
      parseBlocks(it, (String) it.value);
      it.value = null;
      List<Element> itc = it.children;
      if (itc.isEmpty()) {
        continue;
      }
      last = itc.get(itc.size() - 1).type.equals("blank") ? itc.remove(itc.size() - 1) : null;
      Object fap = it.options().remove("first_as_para");
      if (!itc.isEmpty() && itc.get(0).type.equals("p") && !truthy(fap)) {
        if (itc.size() > 1) {
          Element f = itc.get(0).children.get(0);
          f.value = ((String) f.value) + "\n";
        }
        itc.get(0).options().put("transparent", true);
      }
    }

    children = tree.children;
    n = children.size();
    if (n >= 1 && children.get(n - 1).type.equals("dl")) {
      children.get(n - 1).children.addAll(deflist.children);
    } else if (n >= 2 && children.get(n - 1).type.equals("blank") && children.get(n - 2).type.equals("dl")) {
      children.remove(n - 1);
      children.get(n - 2).children.addAll(deflist.children);
    } else {
      children.add(deflist);
    }
    if (last != null) {
      children.add(last);
    }
    return true;
  }

  private static final Map<String, Pattern> FETCH_PATTERNS = new HashMap<>();

  static {
    String[][] kinds = {{"ul", "[+*-]"}, {"ol", "\\d+\\."}, {"dl", ":"}};
    for (String[] k : kinds) {
      FETCH_PATTERNS.put(k[0] + 1, rx("^( {0}" + k[1] + ")(" + PATTERN_TAIL + ")"));
      FETCH_PATTERNS.put(k[0] + 2, rx("^( {0,1}" + k[1] + ")(" + PATTERN_TAIL + ")"));
      FETCH_PATTERNS.put(k[0] + 3, rx("^( {0,2}" + k[1] + ")(" + PATTERN_TAIL + ")"));
      FETCH_PATTERNS.put(k[0] + 4, rx("^( {0,3}" + k[1] + ")(" + PATTERN_TAIL + ")"));
    }
  }

  private static Pattern fetchPattern(String type, int indentation) {
    int k = indentation >= 1 && indentation <= 3 ? indentation : 4;
    return FETCH_PATTERNS.get(type + k);
  }

  // ==== port of parser/kramdown/table.rb ====================================================
  static final Pattern TABLE_SEP_LINE = rx("^([+|: \\t-]*?-[+|: \\t-]*?)[ \\t]*\\n");
  static final Pattern TABLE_HSEP_ALIGN = rx("[ \\t]?(:?)-+(:?)[ \\t]?");
  static final Pattern TABLE_FSEP_LINE = rx("^[+|: \\t=]*?=[+|: \\t=]*?[ \\t]*\\n");
  static final Pattern TABLE_ROW_LINE = rx("^(.*?)[ \\t]*\\n");
  static final String TABLE_PIPE_CHECK_S = "(?:\\||.*?[^\\\\\\n]\\|)";
  static final Pattern TABLE_LINE = rx(TABLE_PIPE_CHECK_S + ".*?\\n");
  static final Pattern TABLE_START = rx("^" + OPT_SPACE + "(?=\\S)" + TABLE_PIPE_CHECK_S + ".*?\\n");
  private static final Pattern LEADING_PIPE = rx("^\\s*\\|");
  private static final Pattern CODE_HTML_SPLIT = rx("(<code.*?>.*?<\\/code>)");
  private static final Pattern UNESCAPED_PIPE = rx("(?<!\\\\)\\|");
  private static final Pattern ESCAPED_PIPE = rx("\\\\\\|");
  private static final Pattern BACKTICKS = rx("`+");
  private static final Pattern PIPE_CHECK_AT_START = rx("^" + TABLE_PIPE_CHECK_S);

  protected boolean parseTable() {
    if (!afterBlockBoundary()) {
      return false;
    }
    int[] savedPos = src.savePos();
    int origPos = src.pos();
    Element table = newBlockEl("table", null, null,
        Element.opts("alignment", new ArrayList<String>(), "location", src.currentLineNumber()));
    String tl = src.check(TABLE_LINE);
    boolean leadingPipe = tl != null && LEADING_PIPE.matcher(tl).find();
    src.scan(TABLE_SEP_LINE);

    List<Element> rows = new ArrayList<>();
    boolean hasFooter = false;
    int columns = 0;

    while (!src.eos()) {
      if (src.check(TABLE_LINE) == null) {
        break;
      }
      if (src.scan(TABLE_SEP_LINE) != null) {
        if (rows.isEmpty()) {
          // ignore separator line at the start
        } else if (alignment(table).isEmpty() && !hasFooter) {
          String sep = src.group(1);
          rows = addContainer(table, rows, hasFooter, "thead", false);
          List<String> al = new ArrayList<>();
          Matcher m = TABLE_HSEP_ALIGN.matcher(sep);
          while (m.find()) {
            String left = m.group(1);
            String right = m.group(2);
            al.add(left.isEmpty() && right.isEmpty() ? "default"
                : right.isEmpty() ? "left" : left.isEmpty() ? "right" : "center");
          }
          table.options().put("alignment", al);
        } else {
          rows = addContainer(table, rows, hasFooter, "tbody", false);
        }
      } else if (src.scan(TABLE_FSEP_LINE) != null) {
        if (!rows.isEmpty()) {
          rows = addContainer(table, rows, hasFooter, "tbody", true);
        }
        hasFooter = true;
      } else if (src.scan(TABLE_ROW_LINE) != null) {
        Element trow = new Element("tr");
        Object[] env = saveEnv();
        List<String> cells = new ArrayList<>();
        String rowText = src.group(1);
        int line = src.currentLineNumber();
        List<String> parts = RubyStr.split(rowText, CODE_HTML_SPLIT, 0);
        for (int i = 0; i < parts.size(); i++) {
          String str = parts.get(i);
          if (i % 2 == 1) {
            appendCell(cells, str);
          } else {
            resetEnv(new StrScanner(str, line), null, null, "raw_text");
            Element r = new Element("root");
            parseSpans(r, null, List.of("codespan"), textType, null);
            for (Element c : r.children) {
              if (c.type.equals("raw_text")) {
                List<String> fl = RubyStr.split((String) c.value, UNESCAPED_PIPE, -1);
                List<String> mapped = new ArrayList<>();
                for (String t : fl) {
                  mapped.add(RubyStr.gsub(t, ESCAPED_PIPE, "|"));
                }
                if (mapped.isEmpty()) {
                  appendCell(cells, null);
                } else {
                  appendCell(cells, mapped.get(0));
                  cells.addAll(mapped.subList(1, mapped.size()));
                }
              } else {
                String v = (String) c.value;
                String max = "";
                Matcher bm = BACKTICKS.matcher(v);
                while (bm.find()) {
                  if (bm.group().compareTo(max) > 0) {
                    max = bm.group();
                  }
                }
                String delim = max + "`";
                String sp = delim.length() > 1 ? " " : "";
                appendCell(cells, delim + sp + v + sp + delim);
              }
            }
          }
        }
        restoreEnv(env);
        if (leadingPipe && RubyStr.strip(cells.get(0)).isEmpty()) {
          cells.remove(0);
        }
        if (RubyStr.strip(cells.get(cells.size() - 1)).isEmpty()) {
          cells.remove(cells.size() - 1);
        }
        for (String cellText : cells) {
          Element tcell = new Element("td");
          tcell.children.add(new Element("raw_text", RubyStr.strip(cellText)));
          trow.children.add(tcell);
        }
        columns = Math.max(columns, cells.size());
        rows.add(trow);
      } else {
        break;
      }
    }

    if (!beforeBlockBoundary()) {
      src.revertPos(savedPos);
      return false;
    }

    // Parse all lines of the table with the code span parser
    Object[] env = saveEnv();
    StrScanner lSrc = new StrScanner(src.extract(origPos, src.pos() - 1), src.currentLineNumber());
    resetEnv(lSrc, null, null, "raw_text");
    Element r = new Element("root");
    parseSpans(r, null, List.of("codespan", "span_html"), textType, null);
    restoreEnv(env);

    // Check if each line has at least one unescaped pipe that is not inside a code span/code
    // HTML element
    boolean pipeOnLine = false;
    while (!r.children.isEmpty()) {
      Element c = r.children.remove(0);
      if (!(c.value instanceof String)) {
        continue;
      }
      List<String> lines = RubyStr.split((String) c.value, "\n", 0);
      if (c.type.equals("codespan")) {
        if (lines.size() > 2 || (lines.size() == 2 && !pipeOnLine)) {
          break;
        } else if (lines.size() == 2 && pipeOnLine) {
          pipeOnLine = false;
        }
      } else {
        if (lines.size() > 1 && !pipeOnLine && !PIPE_CHECK_AT_START.matcher(lines.get(0)).find()) {
          break;
        }
        boolean lastHas = !lines.isEmpty() && PIPE_CHECK_AT_START.matcher(lines.get(lines.size() - 1)).find();
        pipeOnLine = (lines.size() > 1 ? false : pipeOnLine) || lastHas;
      }
    }
    if (!pipeOnLine) {
      src.revertPos(savedPos);
      return false;
    }

    if (!rows.isEmpty()) {
      rows = addContainer(table, rows, hasFooter, hasFooter ? "tfoot" : "tbody", false);
    }

    boolean anyTbody = false;
    for (Element el : table.children) {
      if (el.type.equals("tbody")) {
        anyTbody = true;
      }
    }
    if (!anyTbody) {
      warning("Found table without body on line " + table.opt("location") + " - ignoring it");
      src.revertPos(savedPos);
      return false;
    }

    // adjust all table rows to have equal number of columns, same for alignment defs
    for (Element kind : table.children) {
      for (Element row : kind.children) {
        for (int k = columns - row.children.size(); k > 0; k--) {
          row.children.add(new Element("td"));
        }
      }
    }
    List<String> al = alignment(table);
    if (al.size() > columns) {
      table.options().put("alignment", new ArrayList<>(al.subList(0, columns)));
    } else {
      List<String> nal = new ArrayList<>(al);
      while (nal.size() < columns) {
        nal.add("default");
      }
      table.options().put("alignment", nal);
    }

    tree.children.add(table);
    return true;
  }

  private static void appendCell(List<String> cells, String s) {
    if (cells.isEmpty()) {
      cells.add(s);
    } else {
      cells.set(cells.size() - 1, cells.get(cells.size() - 1) + s);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> alignment(Element table) {
    return (List<String>) table.opt("alignment");
  }

  private static List<Element> addContainer(Element table, List<Element> rows, boolean hasFooter, String type,
      boolean force) {
    if (!hasFooter || !type.equals("tbody") || force) {
      Element cont = new Element(type);
      cont.children = rows;
      table.children.add(cont);
      return new ArrayList<>();
    }
    return rows;
  }

  // ==== port of parser/kramdown/link.rb =====================================================
  private static final Pattern WS_RUN = rx("\\s+");

  protected static String normalizeLinkId(String id) {
    return RubyStr.downcase(RubyStr.gsub(id, WS_RUN, " "));
  }

  static final Pattern LINK_DEFINITION_START = rx("^" + OPT_SPACE
      + "\\[([^\\n\\]]+)\\]:[ \\t]*(?:<(.*?)>|([^\\n]*?\\S[^\\n]*?))(?:(?:[ \\t]*?\\n|[ \\t]+?)[ \\t]*?([\"'])(.+?)\\4)?[ \\t]*?\\n");
  private static final Pattern SPACE_QUOTE = rx("[ \\t]+[\"']");
  /**
   * Equivalent of LINK_DEFINITION_START when no title can match (no quote on the line, next line
   * not starting with optional blanks + quote, no \f \v \r on the line): same groups, linear time.
   */
  static final Pattern LINK_DEFINITION_START_NO_TITLE = rx("^" + OPT_SPACE
      + "\\[([^\\n\\]]+)\\]:[ \\t]*(?:<(.*)>|(\\S(?:[^\\n]*\\S)?))(?:(?!)(.)(.))?[ \\t]*\\n");

  static Pattern chooseLinkDefinitionStart(String s, int pos) {
    int n = s.length();
    int i = pos;
    for (; i < n; i++) {
      char c = s.charAt(i);
      if (c == '\n') {
        break;
      }
      if (c == '"' || c == '\'' || c == '\f' || c == 0x0B || c == '\r') {
        return LINK_DEFINITION_START;
      }
    }
    i++;
    while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
      i++;
    }
    if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
      return LINK_DEFINITION_START;
    }
    return LINK_DEFINITION_START_NO_TITLE;
  }

  protected boolean parseLinkDefinition() {
    String g3 = src.group(3);
    if (g3 != null && SPACE_QUOTE.matcher(g3).find()) {
      return false;
    }
    String g1 = src.group(1);
    String g2 = src.group(2);
    String g5 = src.group(5);
    src.addPos(src.matchedSize());
    String linkId = normalizeLinkId(g1);
    String linkUrl = g2 != null ? g2 : g3;
    if (linkDefs.get(linkId) != null) {
      warning("Duplicate link ID '" + linkId + "' on line " + src.currentLineNumber() + " - overwriting");
    }
    tree.children.add(newBlockEl("eob", "link_def", null, null));
    linkDefs.put(linkId, new Object[] {linkUrl, g5, tree.children.get(tree.children.size() - 1)});
    return true;
  }

  protected void addLink(Element el, String href, String title, String altText, Map<String, Object> ial) {
    el.options().put("ial", ial);
    if (ial != null) {
      updateAttrWithIal(el.attr(), ial);
    }
    if (el.type.equals("a")) {
      el.attr().put("href", href);
    } else {
      el.attr().put("src", href);
      el.attr().put("alt", altText);
      el.children.clear();
    }
    if (title != null) {
      el.attr().put("title", title);
    }
    tree.children.add(el);
  }

  static final Pattern LINK_BRACKET_STOP_RE = rx("(\\])|!?\\[");
  static final Pattern LINK_PAREN_STOP_RE = rx("(\\()|(\\))|\\s(?=['\"])");
  static final Pattern LINK_INLINE_ID_RE = rx("\\s*?\\[([^\\]]+)?\\]");
  static final Pattern LINK_INLINE_TITLE_RE = rxm("\\s*?([\"'])(.+?)\\1\\s*?\\)");
  static final Pattern LINK_START = rx("!?\\[(?=[^^])");
  private static final Pattern OPEN_PAREN = rx("\\(");
  private static final Pattern ANGLE_URL = rx("\\(<(.*?)>");
  private static final Pattern CLOSE_PAREN = rx("\\)");

  protected boolean parseLink() {
    int startLineNumber = src.currentLineNumber();
    String result = src.scan(LINK_START);
    int curPos = src.pos();
    int[] savedPos = src.savePos();
    String linkType = result.startsWith("!") ? "img" : "a";

    // no nested links allowed
    if (linkType.equals("a") && (tree.type.equals("img") || tree.type.equals("a") || stackHasLink())) {
      addText(result);
      return true;
    }
    Element el = new Element(linkType, null, null, Element.opts("location", startLineNumber));

    int[] count = {1};
    boolean found = parseSpans(el, LINK_BRACKET_STOP_RE, null, textType, () -> {
      count[0] += src.group(1) != null ? -1 : 1;
      int imgs = 0;
      for (Element c : el.children) {
        if (c.type.equals("img")) {
          imgs++;
        }
      }
      return count[0] - imgs == 0;
    });
    if (!found) {
      src.revertPos(savedPos);
      addText(result);
      return true;
    }
    String altText = RubyStr.gsub(src.extract(curPos, src.pos()), ESCAPED_CHARS, "\\1");
    src.scan(LINK_BRACKET_STOP_RE);

    // reference style link or no link url
    if (src.scan(LINK_INLINE_ID_RE) != null || src.check(OPEN_PAREN) == null) {
      String g1 = src.group(1);
      boolean emitWarning = g1 == null;
      String linkId = normalizeLinkId(g1 != null ? g1 : altText);
      if (linkDefs.containsKey(linkId)) {
        Object[] linkDef = linkDefs.get(linkId);
        Element eob = (Element) linkDef[2];
        addLink(el, (String) linkDef[0], (String) linkDef[1], altText, eob == null ? null : castMap(eob.opt("ial")));
      } else {
        if (emitWarning) {
          warning("No link definition for link ID '" + linkId + "' found on line " + startLineNumber);
        }
        src.revertPos(savedPos);
        addText(result);
      }
      return true;
    }

    // link url in parentheses
    String linkUrl;
    if (src.scan(ANGLE_URL) != null) {
      linkUrl = src.group(1);
      if (src.scan(CLOSE_PAREN) != null) {
        addLink(el, linkUrl, null, altText, null);
        return true;
      }
    } else {
      StringBuilder lu = new StringBuilder();
      int nrOfBrackets = 0;
      String temp;
      while ((temp = src.scanUntil(LINK_PAREN_STOP_RE)) != null) {
        lu.append(temp);
        if (src.group(2) != null) {
          nrOfBrackets -= 1;
          if (nrOfBrackets == 0) {
            break;
          }
        } else if (src.group(1) != null) {
          nrOfBrackets += 1;
        } else {
          break;
        }
      }
      String l = lu.toString();
      if (l.isEmpty()) {
        throw new IllegalStateException("undefined method `strip!' for nil");
      }
      linkUrl = RubyStr.strip(l.length() >= 2 ? l.substring(1, l.length() - 1) : "");
      if (nrOfBrackets == 0) {
        addLink(el, linkUrl, null, altText, null);
        return true;
      }
    }

    if (src.scan(LINK_INLINE_TITLE_RE) != null) {
      addLink(el, linkUrl, src.group(2), altText, null);
    } else {
      src.revertPos(savedPos);
      addText(result);
    }
    return true;
  }

  private boolean stackHasLink() {
    for (Object[] f : stack) {
      Element t = (Element) f[0];
      if (t != null && (t.type.equals("img") || t.type.equals("a"))) {
        return true;
      }
    }
    return false;
  }

  // ==== port of parser/kramdown/footnote.rb =================================================
  static final Pattern FOOTNOTE_DEFINITION_START = rx("^" + OPT_SPACE + "\\[\\^(" + ALD_ID_NAME + ")\\]:\\s*?(.*?\\n"
      + CODEBLOCK_MATCH_S + ")");

  protected boolean parseFootnoteDefinition() {
    int startLineNumber = src.currentLineNumber();
    String name = src.group(1);
    String body = src.group(2);
    src.addPos(src.matchedSize());
    Element el = new Element("footnote_def", null, null, Element.opts("location", startLineNumber));
    parseBlocks(el, RubyStr.gsub(body, INDENT, ""));
    if (footnotes.get(name) != null) {
      warning("Duplicate footnote name '" + name + "' on line " + startLineNumber + " - overwriting");
    }
    tree.children.add(newBlockEl("eob", "footnote_def", null, null));
    Map<String, Object> fd = new HashMap<>();
    fd.put("content", el);
    fd.put("eob", tree.children.get(tree.children.size() - 1));
    footnotes.put(name, fd);
    return true;
  }

  static final Pattern FOOTNOTE_MARKER_START = rx("\\[\\^(" + ALD_ID_NAME + ")\\]");

  @SuppressWarnings("unchecked")
  protected boolean parseFootnoteMarker() {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    String name = src.group(1);
    Map<String, Object> fnDef = footnotes.get(name);
    if (fnDef != null) {
      if (fnDef.get("eob") != null) {
        Element eob = (Element) fnDef.get("eob");
        Map<String, Object> ial = castMap(eob.opt("ial"));
        updateAttrWithIal(eob.attr(), ial == null ? new LinkedHashMap<>() : ial);
        fnDef.put("attr", eob.attr());
        fnDef.put("options", eob.options());
        fnDef.remove("eob");
      }
      List<Element> marker = (List<Element>) fnDef.get("marker");
      if (marker == null) {
        marker = new ArrayList<>();
        fnDef.put("marker", marker);
      }
      Map<String, Object> o = new HashMap<>(castMap(fnDef.get("options")));
      o.put("name", name);
      o.put("location", startLineNumber);
      marker.add(new Element("footnote", fnDef.get("content"), castMap(fnDef.get("attr")), o));
      tree.children.add(marker.get(marker.size() - 1));
    } else {
      warning("Footnote definition for '" + name + "' not found on line " + startLineNumber);
      addText(src.matched());
    }
    return true;
  }

  // ==== port of parser/kramdown/html.rb (+ Parser module of parser/html.rb) ==================
  static final Map<String, String> HTML_MARKDOWN_ATTR_MAP = Map.of("0", "raw", "1", "default", "span", "span",
      "block", "block");
  static final Pattern TRAILING_WHITESPACE = rx("[ \\t]*\\n");

  @FunctionalInterface
  protected interface TagHandler {
    void handle(Element el, boolean closed, boolean handleBody);
  }

  private final TagHandler kramdownTagHandler = this::handleKramdownHtmlTag;

  private static final Map<String, Pattern> CLOSE_LOOKAHEAD_CACHE = new ConcurrentHashMap<>();

  private static Pattern closeLookahead(String name) {
    Pattern p = CLOSE_LOOKAHEAD_CACHE.get(name);
    if (p == null) {
      if (CLOSE_LOOKAHEAD_CACHE.size() > 5000) {
        CLOSE_LOOKAHEAD_CACHE.clear();
      }
      p = RubyStr.rxmi("(?=<\\/" + name + "\\s*>)");
      CLOSE_LOOKAHEAD_CACHE.put(name, p);
    }
    return p;
  }

  protected void handleKramdownHtmlTag(Element el, boolean closed, boolean handleBody) {
    if (blockIal != null) {
      el.options().put("ial", blockIal);
      blockIal = null;
    }

    String contentModel;
    if (!tree.type.equals("html_element") || !"raw".equals(tree.opt("content_model"))) {
      contentModel = truthy(options.get("parse_block_html")) ? contentModel((String) el.value) : "raw";
    } else {
      contentModel = "raw";
    }
    Object md = el.attr().remove("markdown");
    String val = md == null ? null : HTML_MARKDOWN_ATTR_MAP.get(md.toString());
    if (val != null) {
      contentModel = val.equals("default") ? contentModel((String) el.value) : val;
    }

    if (contentModel.equals("block")) {
      src.scan(TRAILING_WHITESPACE);
    }
    el.options().put("content_model", contentModel);
    el.options().put("is_closed", closed);

    if (!closed && handleBody) {
      switch (contentModel) {
        case "block":
          if (!parseBlocks(el, null)) {
            warning("Found no end tag for '" + el.value + "' (line " + el.opt("location") + ") - auto-closing it");
          }
          break;
        case "span":
          int curpos = src.pos();
          if (src.scanUntil(closeLookahead((String) el.value)) != null) {
            addText(src.extract(curpos, src.pos()), el);
            src.scan(HTML_TAG_CLOSE_RE);
          } else {
            addText(src.rest(), el);
            src.terminate();
            warning("Found no end tag for '" + el.value + "' (line " + el.opt("location") + ") - auto-closing it");
          }
          break;
        default:
          parseRawHtml(el, kramdownTagHandler);
      }
      if (!(tree.type.equals("html_element") && "raw".equals(tree.opt("content_model")))) {
        src.scan(TRAILING_WHITESPACE);
      }
    }
  }

  static final Pattern HTML_BLOCK_START = rx("^" + OPT_SPACE + "<(" + UNAME_STR + "|!--|\\/)");
  private static final Pattern BLOCK_HTML_TAG = Pattern.compile("^" + OPT_SPACE + RubyStr.emb(HTML_TAG_RE), RubyStr.FLAGS);
  private static final Pattern BLOCK_HTML_TAG_CLOSE = Pattern.compile("^" + OPT_SPACE + RubyStr.emb(HTML_TAG_CLOSE_RE),
      RubyStr.FLAGS);

  protected boolean parseBlockHtml() {
    int line = src.currentLineNumber();
    String result;
    if ((result = src.scan(HTML_COMMENT_RE)) != null) {
      tree.children.add(new Element("xml_comment", result, null, Element.opts("category", "block", "location", line)));
      src.scan(TRAILING_WHITESPACE);
      return true;
    } else if (src.check(BLOCK_HTML_TAG) != null && !SPAN_SET.contains(RubyStr.downcase(src.group(1)))) {
      src.addPos(src.matchedSize());
      handleHtmlStartTag(line, kramdownTagHandler);
      if (truthy(options.get("html_to_native"))) {
        // Kramdown::Parser::Html::ElementConverter.convert(@root, @tree.children.last) -- not ported
        throw new KramdownError("html_to_native is not supported by this port");
      }
      return true;
    } else if (src.check(BLOCK_HTML_TAG_CLOSE) != null && !SPAN_SET.contains(RubyStr.downcase(src.group(1)))) {
      String name = RubyStr.downcase(src.group(1));
      if (tree.type.equals("html_element") && name.equals(tree.value)) {
        src.addPos(src.matchedSize());
        throw STOP;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  static final Pattern HTML_SPAN_START = rx("<(" + UNAME_STR + "|!--|\\/|!\\[CDATA\\[)");
  private static final Pattern NEWLINES = rx("\\n+");
  private static final Map<String, Pattern> SPAN_STOP_CACHE = new ConcurrentHashMap<>();

  private static Pattern spanStopRe(String tagName) {
    Pattern p = SPAN_STOP_CACHE.get(tagName);
    if (p == null) {
      if (SPAN_STOP_CACHE.size() > 5000) {
        SPAN_STOP_CACHE.clear();
      }
      String s = "<\\/" + Pattern.quote(tagName) + "\\s*>";
      p = htmlElement(tagName) ? RubyStr.rxi(s) : rx(s);
      SPAN_STOP_CACHE.put(tagName, p);
    }
    return p;
  }

  protected boolean parseSpanHtml() {
    int line = src.currentLineNumber();
    String result;
    if ((result = src.scan(HTML_COMMENT_RE)) != null) {
      tree.children.add(new Element("xml_comment", result, null, Element.opts("category", "span", "location", line)));
    } else if (src.scan(HTML_CDATA_RE) != null) {
      addText(HtmlUtils.escapeHtml(src.group(1), "all"));
    } else if ((result = src.scan(HTML_TAG_CLOSE_RE)) != null) {
      warning("Found invalidly used HTML closing tag for '" + src.group(1) + "' on line " + line);
      addText(result);
    } else if ((result = src.scan(HTML_TAG_RE)) != null) {
      String tagName = src.group(1);
      String g2 = src.group(2);
      boolean closedTag = src.group(4) != null;
      if (htmlElement(RubyStr.downcase(tagName))) {
        tagName = RubyStr.downcase(tagName);
      }
      if (BLOCK_SET.contains(tagName)) {
        warning("Found block HTML tag '" + tagName + "' in span-level text on line " + line);
        addText(result);
        return true;
      }

      Map<String, Object> attrs = parseHtmlAttributes(g2, line, htmlElement(tagName));
      for (Map.Entry<String, Object> e : attrs.entrySet()) {
        String v = (String) e.getValue();
        if (!v.isEmpty()) {
          e.setValue(RubyStr.gsub(v, NEWLINES, " "));
        }
      }

      boolean doParsing;
      if (contentModel(tagName).equals("raw") || "raw".equals(tree.opt("content_model"))) {
        doParsing = false;
      } else {
        doParsing = truthy(options.get("parse_span_html"));
      }
      Object md = attrs.remove("markdown");
      String val = md == null ? null : HTML_MARKDOWN_ATTR_MAP.get(md.toString());
      if (val != null) {
        switch (val) {
          case "block":
            warning("Cannot use block-level parsing in span-level HTML tag (line " + line + ") - using default mode");
            break;
          case "span":
            doParsing = true;
            break;
          case "default":
            doParsing = !contentModel(tagName).equals("raw");
            break;
          case "raw":
            doParsing = false;
            break;
          default:
            break;
        }
      }

      Element el = new Element("html_element", tagName, attrs, Element.opts("category", "span", "location", line,
          "content_model", doParsing ? "span" : "raw", "is_closed", closedTag));
      tree.children.add(el);
      Pattern stopRe = spanStopRe(tagName);
      if (!closedTag && !WITHOUT_BODY_SET.contains((String) el.value)) {
        if (parseSpans(el, stopRe, doParsing ? null : List.of("span_html"), textType, null)) {
          src.scan(stopRe);
        } else {
          warning("Found no end tag for '" + el.value + "' (line " + line + ") - auto-closing it");
          addText(src.rest(), el);
          src.terminate();
        }
      }
      if (truthy(options.get("html_to_native"))) {
        throw new KramdownError("html_to_native is not supported by this port");
      }
    } else {
      addText(src.getch());
    }
    return true;
  }

  /** Html::Parser#handle_html_start_tag(line) {|el, closed, handle_body| } */
  protected void handleHtmlStartTag(Integer line, TagHandler handler) {
    String name = src.group(1);
    String g2 = src.group(2);
    boolean closed = src.group(4) != null;
    if (htmlElement(RubyStr.downcase(name))) {
      name = RubyStr.downcase(name);
    }
    Map<String, Object> attrs = parseHtmlAttributes(g2, line, htmlElement(name));
    Element el = new Element("html_element", name, attrs, Element.opts("category", "block"));
    if (line != null) {
      el.options().put("location", line);
    }
    tree.children.add(el);

    if (!closed && WITHOUT_BODY_SET.contains((String) el.value)) {
      closed = true;
    }
    if (name.equals("script") || name.equals("style")) {
      handleRawHtmlTag(name);
      handler.handle(el, false, false);
    } else {
      handler.handle(el, closed, true);
    }
  }

  /** Html::Parser#parse_html_attributes(str, line, in_html_tag) */
  protected Map<String, Object> parseHtmlAttributes(String str, Integer line, boolean inHtmlTag) {
    Map<String, Object> attrs = new LinkedHashMap<>();
    Matcher m = HTML_ATTRIBUTE_RE.matcher(str);
    while (m.find()) {
      String attr = m.group(1);
      String val = m.group(2);
      String quotedVal = m.group(4);
      if (inHtmlTag) {
        attr = RubyStr.downcase(attr);
      }
      if (attrs.containsKey(attr)) {
        warning("Duplicate HTML attribute '" + attr + "' on line " + (line == null ? "?" : line)
            + " - overwriting previous one");
      }
      attrs.put(attr, val != null ? val : quotedVal != null ? quotedVal : "");
    }
    return attrs;
  }

  /** Html::Parser#handle_raw_html_tag(name) */
  protected void handleRawHtmlTag(String name) {
    int curpos = src.pos();
    Element last = tree.children.get(tree.children.size() - 1);
    if (src.scanUntil(closeLookahead(name)) != null) {
      addText(src.extract(curpos, src.pos()), last, "raw");
      src.scan(HTML_TAG_CLOSE_RE);
    } else {
      addText(src.rest(), last, "raw");
      src.terminate();
      warning("Found no end tag for '" + name + "' - auto-closing it");
    }
  }

  static final Pattern HTML_RAW_START = rx("(?=<(" + UNAME_STR + "|\\/|!--|\\?|!\\[CDATA\\[))");

  /** Html::Parser#parse_raw_html(el, &block) */
  protected void parseRawHtml(Element el, TagHandler handler) {
    stack.add(new Object[] {tree});
    tree = el;
    boolean done = false;
    while (!src.eos() && !done) {
      String result = src.scanUntil(HTML_RAW_START);
      if (result != null) {
        addText(result, tree, "text");
        int line = src.currentLineNumber();
        if ((result = src.scan(HTML_COMMENT_RE)) != null) {
          tree.children.add(new Element("xml_comment", result, null, Element.opts("category", "block", "location", line)));
        } else if ((result = src.scan(HTML_INSTRUCTION_RE)) != null) {
          tree.children.add(new Element("xml_pi", result, null, Element.opts("category", "block", "location", line)));
        } else if (src.scan(HTML_CDATA_RE) != null) {
          tree.children.add(new Element("text", src.group(1), null, Element.opts("cdata", true, "location", line)));
        } else if (src.scan(HTML_TAG_RE) != null) {
          handleHtmlStartTag(line, handler);
        } else if (src.scan(HTML_TAG_CLOSE_RE) != null) {
          String g1 = src.group(1);
          String tv = (String) tree.value;
          if (tv != null && tv.equals(htmlElement(tv) ? RubyStr.downcase(g1) : g1)) {
            done = true;
          } else {
            addText(src.matched(), tree, "text");
            warning("Found invalidly used HTML closing tag for '" + g1 + "' on line " + line + " - ignoring it");
          }
        } else {
          addText(src.getch(), tree, "text");
        }
      } else {
        addText(src.rest(), tree, "text");
        src.terminate();
        if (tree.type.equals("html_element")) {
          warning("Found no end tag for '" + tree.value + "' on line " + tree.opt("location") + " - auto-closing it");
        }
        done = true;
      }
    }
    Object[] f = stack.remove(stack.size() - 1);
    tree = (Element) f[0];
  }

  // ==== port of parser/kramdown/escaped_chars.rb ============================================
  static final Pattern ESCAPED_CHARS = rx("\\\\([\\\\.*_+`<>()\\[\\]{}#!:|\"'$=-])");

  protected boolean parseEscapedChars() {
    src.addPos(src.matchedSize());
    addText(src.group(1));
    return true;
  }

  // ==== port of parser/kramdown/html_entity.rb ==============================================
  protected boolean parseHtmlEntity() {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    String g1 = src.group(1);
    String g2 = src.group(2);
    String g3 = src.group(3);
    String matched = src.matched();
    try {
      Entities.Entity value;
      if (g1 != null) {
        value = Entities.entity(g1);
      } else if (g2 != null) {
        value = Entities.entity(clampCp(g2, 10));
      } else {
        value = Entities.entity(clampCp(g3, 16));
      }
      tree.children.add(new Element("entity", value, null,
          Element.opts("original", matched, "location", startLineNumber)));
    } catch (KramdownError e) {
      tree.children.add(new Element("entity", Entities.entity("amp"), null, Element.opts("location", startLineNumber)));
      addText(matched.substring(1));
    }
    return true;
  }

  static int clampCp(String digits, int radix) {
    try {
      long v = Long.parseLong(digits, radix);
      return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  // ==== port of parser/kramdown/line_break.rb ===============================================
  static final Pattern LINE_BREAK = rx("(  |\\\\\\\\)(?=\\n)");

  protected boolean parseLineBreak() {
    tree.children.add(new Element("br", null, null, Element.opts("location", src.currentLineNumber())));
    src.addPos(src.matchedSize());
    return true;
  }

  // ==== port of parser/kramdown/typographic_symbol.rb =======================================
  static final Pattern TYPOGRAPHIC_SYMS_RE = rx("---|--|\\.\\.\\.|\\\\<<|\\\\>>|<< | >>|<<|>>");
  static final Map<String, String> TYPOGRAPHIC_SYMS_SUBST = Map.of("---", "mdash", "--", "ndash", "...", "hellip",
      "<< ", "laquo_space", " >>", "raquo_space", "<<", "laquo", ">>", "raquo");

  protected boolean parseTypographicSyms() {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    String m = src.matched();
    String val = TYPOGRAPHIC_SYMS_SUBST.get(m);
    if (val != null) {
      tree.children.add(new Element("typographic_sym", val, null, Element.opts("location", startLineNumber)));
    } else if (m.equals("\\<<")) {
      tree.children.add(new Element("entity", Entities.entity("lt"), null, Element.opts("location", startLineNumber)));
      tree.children.add(new Element("entity", Entities.entity("lt"), null, Element.opts("location", startLineNumber)));
    } else {
      tree.children.add(new Element("entity", Entities.entity("gt"), null, Element.opts("location", startLineNumber)));
      tree.children.add(new Element("entity", Entities.entity("gt"), null, Element.opts("location", startLineNumber)));
    }
    return true;
  }

  // ==== port of parser/kramdown/autolink.rb =================================================
  static final String ACHARS = ALNUM + "\\-_.";
  static final Pattern AUTOLINK_START = rx("<((mailto|https?|ftps?):.+?|[" + ACHARS + "]+?@[" + ACHARS + "]+?)>");
  private static final Pattern MAILTO = rx("^mailto:");

  protected boolean parseAutolink() {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    String g1 = src.group(1);
    String href = src.group(2) == null ? "mailto:" + g1 : g1;
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("href", href);
    Element el = new Element("a", null, a, Element.opts("location", startLineNumber));
    addText(RubyStr.sub(g1, MAILTO, ""), el);
    tree.children.add(el);
    return true;
  }

  // ==== port of parser/kramdown/codespan.rb =================================================
  static final Pattern CODESPAN_DELIMITER = rx("`+");
  private static final Pattern CS_PRE = rx("\\s\\Z|\\A\\Z");
  private static final Pattern WS1 = rx("\\s");
  private static final Map<Integer, Pattern[]> BACKTICK_PATTERNS = new ConcurrentHashMap<>();

  protected boolean parseCodespan() {
    int startLineNumber = src.currentLineNumber();
    String result = src.scan(CODESPAN_DELIMITER);
    boolean simple = result.length() == 1;
    int[] savedPos = src.savePos();

    if (simple && csPreAtEnd(src.preMatchEnd()) && src.matches(WS1)) {
      addText(result);
      return true;
    }

    Pattern[] pats = BACKTICK_PATTERNS.computeIfAbsent(result.length(),
        n -> new Pattern[] {rx("`{" + n + "}"), rx("`{" + n + "}\\Z")});
    String text = src.scanUntil(pats[0]);
    if (text != null) {
      text = RubyStr.sub(text, pats[1], "");
      if (!simple) {
        if (text.startsWith(" ")) {
          text = text.substring(1);
        }
        if (text.endsWith(" ")) {
          text = text.substring(0, text.length() - 1);
        }
      }
      tree.children.add(new Element("codespan", text, null,
          Element.opts("codespan_delimiter", result, "location", startLineNumber)));
    } else {
      src.revertPos(savedPos);
      addText(result);
    }
    return true;
  }

  // ==== port of parser/kramdown/emphasis.rb =================================================
  static final Pattern EMPHASIS_START = rx("(?:\\*\\*?|__?)");
  private static final Pattern EM_INTRAWORD = rx("[" + ALPHA + "]-?[" + ALPHA + "]*_*\\z");
  private static final Map<String, Pattern[]> EM_PATTERNS = new ConcurrentHashMap<>();

  private static Pattern[] emPatterns(String delim) {
    return EM_PATTERNS.computeIfAbsent(delim, d -> new Pattern[] {
        rx(Pattern.quote(d)),
        rx(Pattern.quote(d + d) + "(?!" + Pattern.quote(d) + ")"),
        rx(Pattern.quote(d) + "[" + ALNUM + "]")});
  }

  protected boolean parseEmphasis() {
    int startLineNumber = src.currentLineNumber();
    int[] savedPos = src.savePos();

    String result = src.scan(EMPHASIS_START);
    String element = result.length() == 2 ? "strong" : "em";
    String type = result.substring(0, 1);

    if ((type.equals("_") && emIntrawordAtEnd(src.preMatchEnd())) || src.check(WS1) != null
        || tree.type.equals(element) || stackHasType(element)) {
      addText(result);
      return true;
    }

    Object[] r = subParseOrSkip(result, element, type, startLineNumber);
    boolean found = (Boolean) r[0];
    Element el = (Element) r[1];
    Pattern stopRe = (Pattern) r[2];
    if (!found && element.equals("strong") && !tree.type.equals("em")) {
      src.revertPos(savedPos);
      src.addPos(1);
      r = subParseOrSkip(type, "em", type, startLineNumber);
      found = (Boolean) r[0];
      el = (Element) r[1];
      stopRe = (Pattern) r[2];
    }
    if (found) {
      el.options().put("char", type);
      src.scan(stopRe);
      tree.children.add(el);
    } else {
      src.revertPos(savedPos);
      src.addPos(result.length());
      addText(result);
    }
    return true;
  }


  // ---- P4.9: skip emphasis sub-parses that provably fail ------------------------------------
  // A sub_parse for delimiter d can only succeed at a position c >= start where d occurs and the
  // closer's local conditions hold (pre_match doesn't end in whitespace; for em not "dd" without a
  // third d; for "_" not followed by an alnum); nested spans can hide such positions but never
  // create them. If none exists, the sub_parse fails, and a failed sub_parse leaves no trace
  // (pos/line state reverted, warnings truncated) unless its region contains a footnote marker
  // ("[^", which appends to fn_def[:marker]) or a span extension ("{::", which can change
  // options). In that case we return the failure directly instead of parsing to the end of the
  // text for every unmatched opener (the quadratic/cubic blowup on long paragraphs, P0.12).
  private String closerCacheFor;
  private final Map<String, int[]> closerCache = new HashMap<>();
  private int[] statefulStarts;

  private Object[] subParseOrSkip(String delim, String elem, String type, int startLineNumber) {
    if (!closerPossible(delim, elem, type, src.pos())) {
      return new Object[] {Boolean.FALSE, null, emPatterns(delim)[0]};
    }
    return subParse(delim, elem, type, startLineNumber);
  }

  private boolean closerPossible(String delim, String elem, String type, int from) {
    String s = src.string();
    if (closerCacheFor != s) { // identity: a new StrScanner text
      closerCache.clear();
      closerCacheFor = s;
      statefulStarts = positions(s, "[^", "{::");
    }
    if (firstAtOrAfter(statefulStarts, from) >= 0) {
      return true;
    }
    int[] cands = closerCache.computeIfAbsent(delim + "\u0000" + elem + "\u0000" + type, k -> closerCandidates(delim, elem, type));
    return firstAtOrAfter(cands, from) >= 0;
  }

  private int[] closerCandidates(String delim, String elem, String type) {
    String s = src.string();
    Pattern[] pats = emPatterns(delim);
    int n = src.length();
    int[] out = new int[16];
    int k = 0;
    for (int c = s.indexOf(delim); c >= 0 && c < n; c = s.indexOf(delim, c + 1)) {
      if (c > 0 && isRubyWs(s.charAt(c - 1))) {
        continue;
      }
      if (elem.equals("em") && src.matchesAt(pats[1], c)) {
        continue;
      }
      if (type.equals("_") && src.matchesAt(pats[2], c)) {
        continue;
      }
      if (k == out.length) {
        out = java.util.Arrays.copyOf(out, k * 2);
      }
      out[k++] = c;
    }
    return java.util.Arrays.copyOf(out, k);
  }

  private static int[] positions(String s, String... needles) {
    java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
    for (String nd : needles) {
      for (int c = s.indexOf(nd); c >= 0; c = s.indexOf(nd, c + 1)) {
        set.add(c);
      }
    }
    int[] out = new int[set.size()];
    int i = 0;
    for (int v : set) {
      out[i++] = v;
    }
    return out;
  }

  private static int firstAtOrAfter(int[] sorted, int from) {
    int i = java.util.Arrays.binarySearch(sorted, from);
    if (i < 0) {
      i = -i - 1;
    }
    return i < sorted.length ? sorted[i] : -1;
  }

  private Object[] subParse(String delim, String elem, String type, int startLineNumber) {
    Element el = new Element(elem, null, null, Element.opts("location", startLineNumber));
    Pattern[] pats = emPatterns(delim);
    Pattern stopRe = pats[0];
    boolean found = parseSpans(el, stopRe, null, textType, () -> {
      int pe = src.preMatchEnd();
      return (pe <= 0 || !isRubyWs(src.charAt(pe - 1)))
          && (!elem.equals("em") || !src.matches(pats[1]))
          && (!type.equals("_") || !src.matches(pats[2])) && !el.children.isEmpty();
    });
    return new Object[] {found, el, stopRe};
  }


  // ---- O(1) equivalents of regexes Ruby applies to the whole pre_match (P0.12/P4.9): identical
  // results, without materializing or scanning the prefix. Verified against the original regexes
  // by test/kramdown/PreMatchEquivalence.

  /** Ruby /\s/ on one char (ASCII: space, \t, \n, \v, \f, \r). */
  static boolean isRubyWs(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
  }

  /** CS_PRE (/\s\Z|\A\Z/) on string[0, end): empty, or ends with whitespace (covers "\n\Z"). */
  boolean csPreAtEnd(int end) {
    return end <= 0 || isRubyWs(src.charAt(end - 1));
  }

  /** EM_INTRAWORD (/[[:alpha:]]-?[[:alpha:]]*_*\z/) on string[0, end). */
  boolean emIntrawordAtEnd(int end) {
    return emIntraword(src.string(), end);
  }

  static boolean emIntraword(CharSequence s, int end) {
    int i = end;
    while (i > 0 && s.charAt(i - 1) == '_') {
      i--;
    }
    if (i <= 0) {
      return false;
    }
    int c = Character.codePointBefore(s, i);
    if (Character.isAlphabetic(c)) {
      return true;
    }
    if (c == '-') {
      int j = i - 1;
      return j > 0 && Character.isAlphabetic(Character.codePointBefore(s, j));
    }
    return false;
  }

  static boolean emIntrawordRegex(String prefix) {
    return EM_INTRAWORD.matcher(prefix).find();
  }

  static boolean csPreRegex(String prefix) {
    return CS_PRE.matcher(prefix).find();
  }

  private boolean stackHasType(String type) {
    for (Object[] f : stack) {
      Element t = (Element) f[0];
      if (t.type.equals(type)) {
        return true;
      }
    }
    return false;
  }

  // ==== port of parser/kramdown/smart_quotes.rb =============================================
  static final String SQ_PUNCT = "[!\"#$%'()*+,\\-./:;<=>?@\\[\\\\\\]\\^_`{|}~]";
  static final String SQ_CLOSE = "[^ \\\\\\t\\r\\n\\[{(-]";
  static final Pattern[] SQ_RULES_RE = {
      rx("(\"|')(?=[_*]{1,2}\\S)"),
      rx("(\"|')(?=" + SQ_PUNCT + "(?!\\.\\.)" + NWB + ")"),
      rx("(\\s?)\"'(?=\\w)"),
      rx("(\\s?)'\"(?=\\w)"),
      rx("(\\s?)'(?=\\d\\ds)"),
      rx("(\\s)('|\")(?=\\w)"),
      rx("(" + SQ_CLOSE + ")('|\")"),
      rx("(\"|')(?=\\s|s" + WB + "|$)"),
      rxm("(.?)'"),
      rxm("(.?)\"")};
  static final Object[][] SQ_RULES_SUBST = {
      {"lquote1"}, {"rquote1"}, {1, "ldquo", "lsquo"}, {1, "lsquo", "ldquo"}, {1, "rsquo"}, {1, "lquote2"},
      {1, "rquote2"}, {"rquote1"}, {1, "lsquo"}, {1, "ldquo"}};
  static final Map<String, String> SQ_SUBSTS = Map.of(
      "rquote1\"", "rdquo", "rquote1'", "rsquo", "rquote2\"", "rdquo", "rquote2'", "rsquo",
      "lquote1\"", "ldquo", "lquote1'", "lsquo", "lquote2\"", "ldquo", "lquote2'", "lsquo");
  static final Pattern SMART_QUOTES_RE = rx("[^\\\\]?[\"']");

  protected boolean parseSmartQuotes() {
    int startLineNumber = src.currentLineNumber();
    Object[] substs = null;
    for (int i = 0; i < SQ_RULES_RE.length; i++) {
      if (src.scan(SQ_RULES_RE[i]) != null) {
        substs = SQ_RULES_SUBST[i];
        break;
      }
    }
    if (substs == null) {
      throw new IllegalStateException("undefined method `[]' for nil");
    }
    for (Object subst : substs) {
      if (subst instanceof Integer) {
        addText(src.group((Integer) subst));
      } else {
        String s = (String) subst;
        char lastc = s.charAt(s.length() - 1);
        int gi = Character.isDigit(lastc) ? lastc - '0' : 0;
        String val = SQ_SUBSTS.get(s + src.group(gi));
        if (val == null) {
          val = s;
        }
        tree.children.add(new Element("smart_quote", val, null, Element.opts("location", startLineNumber)));
      }
    }
    return true;
  }

  // ==== port of parser/kramdown/math.rb =====================================================
  static final Pattern BLOCK_MATH_START = rxm("^" + OPT_SPACE + "(\\\\)?\\$\\$(.*?)\\$\\$(\\s*?\\n)?");
  private static final Pattern OPT_SPACE_BACKSLASH = rx("^" + OPT_SPACE + "\\\\");

  protected boolean parseBlockMath() {
    int startLineNumber = src.currentLineNumber();
    if (!afterBlockBoundary()) {
      return false;
    } else if (src.group(1) != null) {
      if (src.group(3) != null) {
        src.scan(OPT_SPACE_BACKSLASH);
      }
      return false;
    }
    int[] savedPos = src.savePos();
    src.addPos(src.matchedSize());
    String data = RubyStr.strip(src.group(2));
    if (beforeBlockBoundary()) {
      tree.children.add(newBlockEl("math", data, null, Element.opts("category", "block", "location", startLineNumber)));
      return true;
    } else {
      src.revertPos(savedPos);
      return false;
    }
  }

  static final Pattern INLINE_MATH_START = rxm("\\$\\$(.*?)\\$\\$");

  protected boolean parseInlineMath() {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    tree.children.add(new Element("math", RubyStr.strip(src.group(1)), null,
        Element.opts("category", "span", "location", startLineNumber)));
    return true;
  }

  // ==== port of parser/kramdown/abbreviation.rb =============================================
  static final Pattern ABBREV_DEFINITION_START = rx("^" + OPT_SPACE + "\\*\\[(.+?)\\]:(.*?)\\n");

  protected boolean parseAbbrevDefinition() {
    int startLineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    String abbrevId = src.group(1);
    String abbrevText = RubyStr.strip(src.group(2));
    Map<String, Object> defs = castMap(root.opt("abbrev_defs"));
    if (abbrevGet(defs, abbrevId) != null) {
      warning("Duplicate abbreviation ID '" + abbrevId + "' on line " + startLineNumber + " - overwriting");
    }
    tree.children.add(newBlockEl("eob", "abbrev_def", null, null));
    defs.put(abbrevId, abbrevText);
    castMap(root.opt("abbrev_attr")).put(abbrevId, tree.children.get(tree.children.size() - 1));
    return true;
  }

  private static final Pattern ABBR_KEY_WS = rx("[\\s\\p{Z}]+");

  /** Lookup with the default_proc of root.options[:abbrev_defs]/[:abbrev_attr]. */
  static Object abbrevGet(Map<String, Object> h, String k) {
    if (h.containsKey(k)) {
      return h.get(k);
    }
    String kMod = RubyStr.gsub(k, ABBR_KEY_WS, " ");
    return !k.equals(kMod) ? abbrevGet(h, kMod) : null;
  }

  protected void correctAbbreviationsAttributes() {
    Map<String, Object> attrs = castMap(root.opt("abbrev_attr"));
    for (Map.Entry<String, Object> e : attrs.entrySet()) {
      e.setValue(((Element) e.getValue()).attr());
    }
  }

  private static final Pattern NOT_WORD_OR_BOL = rx("\\W|^");

  protected void replaceAbbreviations(Element el, Pattern[] regexps) {
    Map<String, Object> defs = castMap(root.opt("abbrev_defs"));
    if (defs.isEmpty()) {
      return;
    }
    if (regexps == null) {
      List<String> sorted = new ArrayList<>(defs.keySet());
      sorted.sort(Comparator.comparingInt(String::length).reversed());
      StringBuilder b = new StringBuilder();
      for (String k : sorted) {
        if (b.length() > 0) {
          b.append('|');
        }
        b.append("(?:").append(rubyRegexpEscape(k).replace("\\ ", "[\\s\\p{Z}]+")).append(')');
      }
      Pattern first = rx(b.toString());
      regexps = new Pattern[] {first, rx("(?=(?:\\W|^)" + RubyStr.emb(first) + "(?!\\w))")};
    }
    List<Element> out = new ArrayList<>();
    for (Element child : el.children) {
      if (child.type.equals("text") && !"raw".equals(el.opt("content_model"))) {
        if (regexps[0].matcher((String) child.value).find()) {
          StrScanner strscan = new StrScanner((String) child.value, (Integer) child.opt("location"));
          int textLineno = strscan.currentLineNumber();
          String temp;
          while ((temp = strscan.scanUntil(regexps[1])) != null) {
            int abbrLineno = strscan.currentLineNumber();
            String abbr = strscan.scan(regexps[0]);
            if (abbr == null) {
              temp = temp + strscan.scan(NOT_WORD_OR_BOL);
              abbr = strscan.scan(regexps[0]);
            }
            out.add(new Element("text", temp, null, Element.opts("location", textLineno)));
            out.add(new Element("abbreviation", abbr, null, Element.opts("location", abbrLineno)));
            textLineno = strscan.currentLineNumber();
          }
          out.add(new Element("text", strscan.rest(), null, Element.opts("location", textLineno)));
        } else {
          out.add(child);
        }
      } else {
        replaceAbbreviations(child, regexps);
        out.add(child);
      }
    }
    el.children = out;
  }

  /** Ruby Regexp.escape */
  static String rubyRegexpEscape(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '[': case ']': case '{': case '}': case '(': case ')': case '|': case '-': case '*': case '.':
        case '\\': case '?': case '+': case '^': case '$': case '#':
          b.append('\\').append(c);
          break;
        case ' ':
          b.append("\\ ");
          break;
        case '\t':
          b.append("\\t");
          break;
        case '\f':
          b.append("\\f");
          break;
        case 0x0B:
          b.append("\\x0B");
          break;
        case '\n':
          b.append("\\n");
          break;
        case '\r':
          b.append("\\r");
          break;
        default:
          b.append(c);
      }
    }
    return b.toString();
  }

  // ==== parser registry (define_parser calls of all parser/kramdown/*.rb files) ==============
  static {
    defineParser("blank_line", BLANK_LINE, null, KramdownParser::parseBlankLine);
    defineParser("eob_marker", EOB_MARKER, null, KramdownParser::parseEobMarker);
    defineParser("paragraph", PARAGRAPH_START, null, KramdownParser::parseParagraph);
    defineParser("setext_header", SETEXT_HEADER_START, null, KramdownParser::parseSetextHeader);
    defineParser("atx_header", ATX_HEADER_START, null, KramdownParser::parseAtxHeader);
    defineParser("blockquote", BLOCKQUOTE_START, null, KramdownParser::parseBlockquote);
    defineParser("table", TABLE_START, null, KramdownParser::parseTable);
    defineParser("codeblock", INDENT, null, KramdownParser::parseCodeblock);
    defineParser("codeblock_fenced", FENCED_CODEBLOCK_START, null, KramdownParser::parseCodeblockFenced);
    defineParser("horizontal_rule", HR_START, null, KramdownParser::parseHorizontalRule);
    defineParser("list", LIST_START, null, KramdownParser::parseList);
    defineParser("definition_list", DEFINITION_LIST_START, null, KramdownParser::parseDefinitionList);
    defineParser("link_definition", LINK_DEFINITION_START, null, KramdownParser::parseLinkDefinition);
    defineParser("link", LINK_START, "!?\\[", KramdownParser::parseLink);
    defineParser("block_extensions", BLOCK_EXTENSIONS_START, null, KramdownParser::parseBlockExtensions);
    defineParser("span_extensions", SPAN_EXTENSIONS_START, "\\{:", KramdownParser::parseSpanExtensions);
    defineParser("footnote_definition", FOOTNOTE_DEFINITION_START, null, KramdownParser::parseFootnoteDefinition);
    defineParser("footnote_marker", FOOTNOTE_MARKER_START, "\\[", KramdownParser::parseFootnoteMarker);
    defineParser("block_html", HTML_BLOCK_START, null, KramdownParser::parseBlockHtml);
    defineParser("span_html", HTML_SPAN_START, "<", KramdownParser::parseSpanHtml);
    defineParser("escaped_chars", ESCAPED_CHARS, "\\\\", KramdownParser::parseEscapedChars);
    defineParser("html_entity", HTML_ENTITY_RE, "&", KramdownParser::parseHtmlEntity);
    defineParser("line_break", LINE_BREAK, "(  |\\\\\\\\)(?=\\n)", KramdownParser::parseLineBreak);
    defineParser("typographic_syms", TYPOGRAPHIC_SYMS_RE, "--|\\.\\.\\.|(?:\\\\| )?(?:<<|>>)",
        KramdownParser::parseTypographicSyms);
    defineParser("autolink", AUTOLINK_START, "<", KramdownParser::parseAutolink);
    defineParser("codespan", CODESPAN_DELIMITER, "`", KramdownParser::parseCodespan);
    defineParser("emphasis", EMPHASIS_START, "\\*|_", KramdownParser::parseEmphasis);
    defineParser("smart_quotes", SMART_QUOTES_RE, "[^\\\\]?[\"']", KramdownParser::parseSmartQuotes);
    defineParser("block_math", BLOCK_MATH_START, null, KramdownParser::parseBlockMath);
    defineParser("inline_math", INLINE_MATH_START, "\\$", KramdownParser::parseInlineMath);
    defineParser("abbrev_definition", ABBREV_DEFINITION_START, null, KramdownParser::parseAbbrevDefinition);

    // performance prefilters: necessary conditions of the start regexes (they all begin with ^ at pos)
    prefilter("link_definition", (s, pos) -> charAt(s, skip3(s, pos), '['));
    PARSERS.get("link_definition").chooser = KramdownParser::chooseLinkDefinitionStart;
    prefilter("footnote_definition", (s, pos) -> {
      int i = skip3(s, pos);
      return charAt(s, i, '[') && charAt(s, i + 1, '^');
    });
    prefilter("abbrev_definition", (s, pos) -> {
      int i = skip3(s, pos);
      return charAt(s, i, '*') && charAt(s, i + 1, '[');
    });
    prefilter("block_math", (s, pos) -> {
      int i = skip3(s, pos);
      if (charAt(s, i, '\\')) {
        i++;
      }
      return charAt(s, i, '$') && charAt(s, i + 1, '$');
    });
    prefilter("table", (s, pos) -> {
      for (int i = pos, n = s.length(); i < n; i++) {
        char c = s.charAt(i);
        if (c == '|') {
          return true;
        } else if (c == '\n') {
          return false;
        }
      }
      return false;
    });
    prefilter("setext_header", (s, pos) -> {
      int nl = s.indexOf('\n', pos);
      return nl >= 0 && (charAt(s, nl + 1, '-') || charAt(s, nl + 1, '='));
    });
    prefilter("horizontal_rule", (s, pos) -> {
      int i = skip3(s, pos);
      return i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '-' || s.charAt(i) == '_');
    });
    prefilter("block_html", (s, pos) -> charAt(s, skip3(s, pos), '<'));
    prefilter("block_extensions", (s, pos) -> {
      int i = skip3(s, pos);
      return charAt(s, i, '{') && charAt(s, i + 1, ':');
    });
    prefilter("definition_list", (s, pos) -> charAt(s, skip3(s, pos), ':'));
    prefilter("blockquote", (s, pos) -> charAt(s, skip3(s, pos), '>'));
    prefilter("list", (s, pos) -> {
      int i = skip3(s, pos);
      if (i >= s.length()) {
        return false;
      }
      char c = s.charAt(i);
      return c == '+' || c == '*' || c == '-' || (c >= '0' && c <= '9');
    });
  }
}
