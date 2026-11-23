package org.hl7.fhir.jekyll.kramdown;

import static org.hl7.fhir.jekyll.kramdown.HtmlUtils.escapeHtml;
import static org.hl7.fhir.jekyll.kramdown.HtmlUtils.htmlAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of kramdown-2.6.1/lib/kramdown/converter/html.rb (Kramdown::Converter::Html) together with
 * converter/base.rb (Kramdown::Converter::Base) and converter/math_engine/mathjax.rb.
 */
public final class HtmlConverter {

  // ==== port of converter/base.rb ===========================================================
  final Map<String, Object> data = new HashMap<>();
  final Map<String, Object> options;
  final Element root;
  final List<String> warnings = new ArrayList<>();

  /** Base.convert(tree, options) -> [result, warnings] */
  @SuppressWarnings("unchecked")
  public static Object[] convert(Element tree, Map<String, Object> options) {
    Map<String, Object> o = new LinkedHashMap<>(options);
    Object treeOpts = tree.opt("options");
    if (treeOpts != null) {
      o.putAll((Map<String, Object>) treeOpts);
    }
    HtmlConverter converter = new HtmlConverter(tree, Options.merge(o));
    if (!((String) converter.options.get("template")).isEmpty()) {
      throw new KramdownError("templates are not supported by this port");
    }
    String result = converter.convert(tree, -converter.indent);
    return new Object[] {result, converter.warnings};
  }

  void warning(String text) {
    warnings.add(text);
  }

  private static final Pattern NO_TOC = RubyStr.rx(HtmlConstants.WB + "no_toc" + HtmlConstants.WB);

  @SuppressWarnings("unchecked")
  boolean inToc(Element el) {
    List<Object> levels = (List<Object>) options.get("toc_levels");
    Object lvl = el.opt("level");
    boolean contains = false;
    for (Object l : levels) {
      if (((Number) l).longValue() == ((Number) lvl).longValue()) {
        contains = true;
      }
    }
    Object cls = el.attr().get("class");
    return contains && !NO_TOC.matcher(cls == null ? "" : cls.toString()).find();
  }

  int outputHeaderLevel(int level) {
    long off = ((Number) options.get("header_offset")).longValue();
    return (int) Math.max(Math.min(level + off, 6), 1);
  }

  private static final Pattern LANGUAGE_ANY = RubyStr.rx(HtmlConstants.WB + "language-\\S+");
  private static final Pattern LANGUAGE_CAPTURE = RubyStr.rx(HtmlConstants.WB + "language-(\\S+)");

  static String extractCodeLanguage(Map<String, Object> attr) {
    Object c = attr.get("class");
    if (c != null && LANGUAGE_ANY.matcher(c.toString()).find()) {
      Matcher m = LANGUAGE_CAPTURE.matcher(c.toString());
      m.find();
      return m.group(1);
    }
    return null;
  }

  static String extractCodeLanguageBang(Map<String, Object> attr) {
    String lang = extractCodeLanguage(attr);
    if (lang != null) {
      String c = RubyStr.strip(RubyStr.sub(attr.get("class").toString(), LANGUAGE_ANY, ""));
      attr.put("class", c);
      if (c.isEmpty()) {
        attr.remove("class");
      }
    }
    return lang;
  }

  String highlightCode(String text, String lang, String type, Map<String, Object> opts) {
    Object sh = options.get("syntax_highlighter");
    if (sh == null) {
      return null;
    }
    if (sh.equals("rouge")) {
      return RougeHighlighter.call(this, text, lang, type, opts);
    } else if (sh.equals("minted")) {
      throw new KramdownError("minted syntax highlighter is not supported by this port");
    } else {
      warning("The configured syntax highlighter " + sh + " is not available.");
      return null;
    }
  }

  String formatMath(Element el, int indent) {
    Object me = options.get("math_engine");
    if (me == null) {
      return null;
    }
    if (me.equals("mathjax")) {
      return mathjax(el, indent);
    }
    warning("The configured math engine " + me + " is not available.");
    return null;
  }

  private Map<String, Integer> usedIds;
  private static final Pattern LEADING_NON_ALPHA = RubyStr.rx("^[^a-zA-Z]+");

  String generateId(String str) {
    if (KramdownParser.truthy(options.get("transliterated_header_ids"))) {
      // Utils::Unidecoder does `gem 'stringex'`, which is not installed in the reference image:
      // Gem::MissingSpecError (a LoadError) aborts the conversion.
      throw new KramdownError("Could not find 'stringex' (>= 0) among installed gems (transliterated_header_ids)");
    }
    String genId = basicGenerateId(str);
    if (genId.isEmpty()) {
      genId = "section";
    }
    if (usedIds == null) {
      usedIds = new HashMap<>();
    }
    if (usedIds.containsKey(genId)) {
      int n = usedIds.get(genId) + 1;
      usedIds.put(genId, n);
      genId = genId + "-" + n;
    } else {
      usedIds.put(genId, 0);
    }
    return options.get("auto_id_prefix") + genId;
  }

  static String basicGenerateId(String str) {
    String genId = RubyStr.gsub(str, LEADING_NON_ALPHA, "");
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < genId.length(); i++) {
      char c = genId.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '-') {
        b.append(c == ' ' ? '-' : c);
      } else if (Character.isHighSurrogate(c)) {
        i++;
      }
    }
    return RubyStr.downcase(b.toString());
  }

  private static final Map<String, Integer> SMART_QUOTE_INDICES = Map.of("lsquo", 0, "rsquo", 1, "ldquo", 2, "rdquo", 3);

  @SuppressWarnings("unchecked")
  Entities.Entity smartQuoteEntity(Element el) {
    List<Object> sq = (List<Object>) options.get("smart_quotes");
    Object res = sq.get(SMART_QUOTE_INDICES.get((String) el.value));
    return Entities.entity(res);
  }

  // ==== port of converter/html.rb ===========================================================
  private int footnoteCounter;
  private final int footnoteStart;
  private final List<Object[]> footnotes = new ArrayList<>();
  private final Map<String, Object[]> footnotesByName = new HashMap<>();
  private String footnoteLocation;
  private final List<Object[]> toc = new ArrayList<>();
  private Object[] tocCode;
  final int indent = 2;
  private final List<Element> stack = new ArrayList<>();
  private final String highlighterClass;
  private final String entityOutput;

  HtmlConverter(Element root, Map<String, Object> options) {
    this.options = options;
    this.root = root;
    this.footnoteStart = ((Number) options.get("footnote_nr")).intValue();
    this.footnoteCounter = footnoteStart;
    Object sh = options.get("syntax_highlighter");
    this.highlighterClass = " highlighter-" + (sh == null ? "" : sh);
    Object eo = options.get("entity_output");
    this.entityOutput = eo == null ? null : eo.toString();
  }

  private String entityToStr(Entities.Entity e) {
    return HtmlUtils.entityToStr(e, null, entityOutput);
  }

  private String entityToStr(Entities.Entity e, String original) {
    return HtmlUtils.entityToStr(e, original, entityOutput);
  }

  String convert(Element el, int ind) {
    switch (el.type) {
      case "blank":
        return "\n";
      case "text":
        return convertText(el);
      case "p":
        return convertP(el, ind);
      case "codeblock":
        return convertCodeblock(el, ind);
      case "blockquote":
        return formatAsIndentedBlockHtml("blockquote", el.attr(), inner(el, ind), ind);
      case "header":
        return convertHeader(el, ind);
      case "hr":
        return RubyStr.spaces(ind) + "<hr" + htmlAttributes(el.attr()) + " />\n";
      case "ul":
      case "ol":
        return convertUl(el, ind);
      case "dl":
        return formatAsIndentedBlockHtml("dl", el.attr(), inner(el, ind), ind);
      case "li":
      case "dd":
        return convertLi(el, ind);
      case "dt":
        return convertDt(el, ind);
      case "html_element":
        return convertHtmlElement(el, ind);
      case "xml_comment":
      case "xml_pi":
        return convertXmlComment(el, ind);
      case "table":
      case "thead":
      case "tbody":
      case "tfoot":
      case "tr":
        return formatAsIndentedBlockHtml(el.type, el.attr(), inner(el, ind), ind);
      case "td":
        return convertTd(el, ind);
      case "comment":
        return convertComment(el, ind);
      case "br":
        return "<br />";
      case "a":
        return formatAsSpanHtml("a", el.attr(), inner(el, ind));
      case "img":
        return "<img" + htmlAttributes(el.attr()) + " />";
      case "codespan":
        return convertCodespan(el);
      case "footnote":
        return convertFootnote(el);
      case "raw":
        return convertRaw(el);
      case "em":
      case "strong":
        return formatAsSpanHtml(el.type, el.attr(), inner(el, ind));
      case "entity":
        return entityToStr((Entities.Entity) el.value, (String) el.opt("original"));
      case "typographic_sym":
        return convertTypographicSym(el);
      case "smart_quote":
        return entityToStr(smartQuoteEntity(el));
      case "math":
        return convertMath(el, ind);
      case "abbreviation":
        return convertAbbreviation(el);
      case "root":
        return convertRoot(el, ind);
      default:
        throw new KramdownError("undefined method 'convert_" + el.type + "' for an instance of Kramdown::Converter::Html");
    }
  }

  String inner(Element el, int ind) {
    StringBuilder result = new StringBuilder();
    ind += indent;
    stack.add(el);
    for (Element innerEl : el.children) {
      result.append(convert(innerEl, ind));
    }
    stack.remove(stack.size() - 1);
    return result.toString();
  }

  private String convertText(Element el) {
    String escaped = escapeHtml((String) el.value, "text");
    return KramdownParser.truthy(options.get("remove_line_breaks_for_cjk")) ? HtmlUtils.fixCjkLineBreak(escaped) : escaped;
  }

  @SuppressWarnings("unchecked")
  private String convertP(Element el, int ind) {
    if (KramdownParser.truthy(el.opt("transparent"))) {
      return inner(el, ind);
    } else if (el.children.size() == 1 && el.children.get(0).type.equals("img") && hasRef(el.children.get(0), "standalone")) {
      return convertStandaloneImage(el, ind);
    } else {
      return formatAsBlockHtml("p", el.attr(), inner(el, ind), ind);
    }
  }

  @SuppressWarnings("unchecked")
  private static boolean hasRef(Element el, String ref) {
    Map<String, Object> ial = (Map<String, Object>) el.opt("ial");
    if (ial == null) {
      return false;
    }
    List<Object> refs = (List<Object>) ial.get(Element.REFS);
    return refs != null && refs.contains(ref);
  }

  private String convertStandaloneImage(Element el, int ind) {
    Map<String, Object> figureAttr = new LinkedHashMap<>(el.attr());
    Map<String, Object> imageAttr = new LinkedHashMap<>(el.children.get(0).attr());
    if (imageAttr.containsKey("class") && !figureAttr.containsKey("class")) {
      figureAttr.put("class", imageAttr.remove("class"));
    }
    if (imageAttr.containsKey("id") && !figureAttr.containsKey("id")) {
      figureAttr.put("id", imageAttr.remove("id"));
    }
    Object alt = imageAttr.get("alt");
    String body = RubyStr.spaces(ind + indent) + "<img" + htmlAttributes(imageAttr) + " />\n"
        + RubyStr.spaces(ind + indent) + "<figcaption>" + (alt == null ? "" : alt) + "</figcaption>\n";
    return formatAsIndentedBlockHtml("figure", figureAttr, body, ind);
  }

  private static final Pattern SHOW_WS = RubyStr.rx(HtmlConstants.WB + "show-whitespaces" + HtmlConstants.WB);
  private static final Pattern WS_GROUPS = RubyStr.rx("(?:(^[ \\t]+)|([ \\t]+$)|([ \\t]+))");

  private String convertCodeblock(Element el, int ind) {
    Map<String, Object> attr = new LinkedHashMap<>(el.attr());
    String lang = extractCodeLanguageBang(attr);
    Map<String, Object> hlOpts = new HashMap<>();
    String olang = (String) el.opt("lang");
    String highlightedCode = highlightCode((String) el.value, olang != null ? olang : lang, "block", hlOpts);

    if (highlightedCode != null) {
      Object dl = hlOpts.get("default_lang");
      addSyntaxHighlighterToClassAttr(attr, lang != null ? lang : (dl == null ? null : dl.toString()));
      return RubyStr.spaces(ind) + "<div" + htmlAttributes(attr) + ">" + highlightedCode + RubyStr.spaces(ind) + "</div>\n";
    } else {
      String result = RubyStr.chomp(escapeHtml((String) el.value, "all"));
      Object cls = el.attr().get("class");
      if (SHOW_WS.matcher(cls == null ? "" : cls.toString()).find()) {
        result = RubyStr.gsub(result, WS_GROUPS, m -> {
          String suffix = m.group(1) != null ? "-l" : (m.group(2) != null ? "-r" : "");
          StringBuilder b = new StringBuilder();
          for (char c : m.group().toCharArray()) {
            if (c == '\t') {
              b.append("<span class=\"ws-tab").append(suffix).append("\">\t</span>");
            } else if (c == ' ') {
              b.append("<span class=\"ws-space").append(suffix).append("\">&#8901;</span>");
            }
          }
          return b.toString();
        });
      }
      Map<String, Object> codeAttr = new LinkedHashMap<>();
      if (lang != null) {
        codeAttr.put("class", "language-" + lang);
      }
      return RubyStr.spaces(ind) + "<pre" + htmlAttributes(attr) + "><code" + htmlAttributes(codeAttr) + ">" + result
          + "\n</code></pre>\n";
    }
  }

  private String convertHeader(Element el, int ind) {
    Map<String, Object> attr = new LinkedHashMap<>(el.attr());
    if (KramdownParser.truthy(options.get("auto_ids")) && attr.get("id") == null) {
      attr.put("id", generateId((String) el.opt("raw_text")));
    }
    Object headerLinks = options.get("header_links");
    if (KramdownParser.truthy(headerLinks) && attr.get("id") != null
        && attr.get("id").toString().length() > 0) {
      Element link = new Element("a", null, null, null);
      link.attr().put("href", "#" + attr.get("id"));
      // 2.6.1: @options[:header_links][:content] / [:position] (the option is a hash once validated)
      Map<?, ?> hl = headerLinks instanceof Map ? (Map<?, ?>) headerLinks : Map.of();
      String headerLinkText = Options.toS(hl.get("content"));
      if (!headerLinkText.isEmpty()) {
        link.children.add(new Element("text", headerLinkText));
      }
      if (Options.toS(hl.get("position")).equals("start")) {
        el.children.add(0, link);
      } else {
        el.children.add(link);
      }
    }
    if (attr.get("id") != null && inToc(el)) {
      toc.add(new Object[] {el.opt("level"), attr.get("id"), el.children});
    }
    int level = outputHeaderLevel(((Number) el.opt("level")).intValue());
    return formatAsBlockHtml("h" + level, attr, inner(el, ind), ind);
  }

  private static String randomPlaceholder() {
    ThreadLocalRandom r = ThreadLocalRandom.current();
    StringBuilder b = new StringBuilder(129);
    for (int i = 0; i <= 128; i++) {
      b.append(Character.forDigit(r.nextInt(36), 36));
    }
    return b.toString();
  }

  @SuppressWarnings("unchecked")
  private String convertUl(Element el, int ind) {
    if (tocCode == null && hasRef(el, "toc")) {
      tocCode = new Object[] {el.type, el.attr(), randomPlaceholder()};
      return (String) tocCode[2];
    } else if (footnoteLocation == null && hasRef(el, "footnotes")) {
      footnoteLocation = randomPlaceholder();
      return footnoteLocation;
    } else {
      return formatAsIndentedBlockHtml(el.type, el.attr(), inner(el, ind), ind);
    }
  }

  private static final Pattern NL_END = RubyStr.rx("\\n\\Z");

  private String convertLi(Element el, int ind) {
    StringBuilder output = new StringBuilder();
    output.append(RubyStr.spaces(ind)).append('<').append(el.type).append(htmlAttributes(el.attr())).append('>');
    String res = inner(el, ind);
    if (el.children.isEmpty() || (el.children.get(0).type.equals("p")
        && KramdownParser.truthy(el.children.get(0).opt("transparent")))) {
      output.append(res).append(NL_END.matcher(res).find() ? RubyStr.spaces(ind) : "");
    } else {
      output.append('\n').append(res).append(RubyStr.spaces(ind));
    }
    output.append("</").append(el.type).append(">\n");
    return output.toString();
  }

  private static final Pattern AUTO_IDS_REF = RubyStr.rx("\\Aauto_ids(?:-([\\w-]+))?");

  @SuppressWarnings("unchecked")
  private String convertDt(Element el, int ind) {
    Map<String, Object> attr = new LinkedHashMap<>(el.attr());
    Element parent = stack.get(stack.size() - 1);
    Map<String, Object> ial = (Map<String, Object>) parent.opt("ial");
    if (attr.get("id") == null && ial != null && ial.get(Element.REFS) != null) {
      for (Object ref : (List<Object>) ial.get(Element.REFS)) {
        Matcher m = AUTO_IDS_REF.matcher(ref.toString());
        if (m.find()) {
          String pre = m.group(1) == null ? "" : m.group(1);
          attr.put("id", RubyStr.lstrip(pre + basicGenerateId((String) el.opt("raw_text"))));
          break;
        }
      }
    }
    return formatAsBlockHtml("dt", attr, inner(el, ind), ind);
  }

  private String convertHtmlElement(Element el, int ind) {
    String res = inner(el, ind);
    String name = (String) el.value;
    if ("span".equals(el.opt("category"))) {
      return "<" + name + htmlAttributes(el.attr())
          + (res.isEmpty() && HtmlConstants.WITHOUT_BODY_SET.contains(name) ? " />" : ">" + res + "</" + name + ">");
    } else {
      StringBuilder output = new StringBuilder();
      Element top = stack.get(stack.size() - 1);
      boolean notRaw = !top.type.equals("html_element") || !"raw".equals(top.opt("content_model"));
      if (notRaw) {
        output.append(RubyStr.spaces(ind));
      }
      output.append('<').append(name).append(htmlAttributes(el.attr()));
      if (KramdownParser.truthy(el.opt("is_closed")) && "raw".equals(el.opt("content_model"))) {
        output.append(" />");
      } else if (!res.isEmpty() && !"block".equals(el.opt("content_model"))) {
        output.append('>').append(res).append("</").append(name).append('>');
      } else if (!res.isEmpty()) {
        output.append(">\n").append(RubyStr.chomp(res)).append('\n').append(RubyStr.spaces(ind)).append("</")
            .append(name).append('>');
      } else if (HtmlConstants.WITHOUT_BODY_SET.contains(name)) {
        output.append(" />");
      } else {
        output.append("></").append(name).append('>');
      }
      if (notRaw) {
        output.append('\n');
      }
      return output.toString();
    }
  }

  private String convertXmlComment(Element el, int ind) {
    Element top = stack.get(stack.size() - 1);
    if ("block".equals(el.opt("category"))
        && (!top.type.equals("html_element") || !"raw".equals(top.opt("content_model")))) {
      return RubyStr.spaces(ind) + el.value + "\n";
    } else {
      return (String) el.value;
    }
  }

  @SuppressWarnings("unchecked")
  private String convertTd(Element el, int ind) {
    String res = inner(el, ind);
    String type = stack.get(stack.size() - 2).type.equals("thead") ? "th" : "td";
    Map<String, Object> attr = el.attr();
    List<String> alignment = (List<String>) stack.get(stack.size() - 3).opt("alignment");
    int idx = indexOfIdentity(stack.get(stack.size() - 1).children, el);
    String al = idx >= 0 && idx < alignment.size() ? alignment.get(idx) : null;
    if (!"default".equals(al)) {
      attr = new LinkedHashMap<>(el.attr());
      attr.put("style", (attr.containsKey("style") ? attr.get("style") + "; " : "") + "text-align: " + (al == null ? "" : al));
    }
    return formatAsBlockHtml(type, attr, res.isEmpty() ? entityToStr(Entities.entity("nbsp")) : res, ind);
  }

  private static int indexOfIdentity(List<Element> l, Element e) {
    for (int i = 0; i < l.size(); i++) {
      if (l.get(i) == e) {
        return i;
      }
    }
    return -1;
  }

  private String convertComment(Element el, int ind) {
    if ("block".equals(el.opt("category"))) {
      return RubyStr.spaces(ind) + "<!-- " + el.value + " -->\n";
    } else {
      return "<!-- " + el.value + " -->";
    }
  }

  private String convertCodespan(Element el) {
    Map<String, Object> attr = new LinkedHashMap<>(el.attr());
    String lang = extractCodeLanguage(attr);
    Map<String, Object> hlOpts = new HashMap<>();
    String result = highlightCode((String) el.value, lang, "span", hlOpts);
    if (result != null) {
      Object dl = hlOpts.get("default_lang");
      addSyntaxHighlighterToClassAttr(attr, lang != null ? lang : (dl == null ? null : dl.toString()));
    } else {
      result = escapeHtml((String) el.value, "all");
    }
    return formatAsSpanHtml("code", attr, result);
  }

  private String convertFootnote(Element el) {
    String repeat = "";
    String name = options.get("footnote_prefix") + (String) el.opt("name");
    Object[] footnote = footnotesByName.get(name);
    int number;
    if (footnote != null) {
      number = (Integer) footnote[2];
      footnote[3] = (Integer) footnote[3] + 1;
      repeat = ":" + footnote[3];
    } else {
      number = footnoteCounter;
      footnoteCounter++;
      Object[] f = new Object[] {name, el.value, number, 0};
      footnotes.add(f);
      footnotesByName.put(name, f);
    }
    String formattedLinkText = ((String) options.get("footnote_link_text")).replace("%s", String.valueOf(number));
    return "<sup id=\"fnref:" + name + repeat + "\"><a href=\"#fn:" + name
        + "\" class=\"footnote\" rel=\"footnote\" role=\"doc-noteref\">" + formattedLinkText + "</a></sup>";
  }

  @SuppressWarnings("unchecked")
  private String convertRaw(Element el) {
    List<String> t = (List<String>) el.opt("type");
    if (t == null || t.isEmpty() || t.contains("html")) {
      return el.value + ("block".equals(el.opt("category")) ? "\n" : "");
    } else {
      return "";
    }
  }

  @SuppressWarnings("unchecked")
  private String convertTypographicSym(Element el) {
    Map<Object, Object> ts = (Map<Object, Object>) options.get("typographic_symbols");
    Object result = ts.get(el.value);
    if (result != null) {
      return escapeHtml(result.toString(), "text");
    }
    switch ((String) el.value) {
      case "laquo_space":
        return entityToStr(Entities.entity("laquo")) + entityToStr(Entities.entity("nbsp"));
      case "raquo_space":
        return entityToStr(Entities.entity("nbsp")) + entityToStr(Entities.entity("raquo"));
      default:
        return entityToStr(Entities.entity((String) el.value));
    }
  }

  private String convertMath(Element el, int ind) {
    String result = formatMath(el, ind);
    if (result != null) {
      return result;
    }
    Map<String, Object> attr = new LinkedHashMap<>(el.attr());
    Object c = attr.get("class");
    attr.put("class", RubyStr.lstrip((c == null ? "" : c) + " kdmath"));
    if ("block".equals(el.opt("category"))) {
      return formatAsBlockHtml("div", attr, "$$\n" + el.value + "\n$$", ind);
    } else {
      return formatAsSpanHtml("span", attr, "$" + el.value + "$");
    }
  }

  /** port of converter/math_engine/mathjax.rb */
  private String mathjax(Element el, int ind) {
    String value = escapeHtml((String) el.value, "all");
    boolean block = "block".equals(el.opt("category"));
    String result = block ? "\\[" + value + "\\]\n" : "\\(" + value + "\\)";
    if (!el.hasAttr()) {
      return result;
    } else if (block) {
      return formatAsBlockHtml("div", el.attr(), result, ind);
    } else {
      return formatAsSpanHtml("span", el.attr(), "$" + el.value + "$");
    }
  }

  @SuppressWarnings("unchecked")
  private String convertAbbreviation(Element el) {
    String title = (String) KramdownParser.abbrevGet((Map<String, Object>) root.opt("abbrev_defs"), (String) el.value);
    Object a = KramdownParser.abbrevGet((Map<String, Object>) root.opt("abbrev_attr"), (String) el.value);
    Map<String, Object> attr = new LinkedHashMap<>((Map<String, Object>) a);
    if (!title.isEmpty()) {
      attr.put("title", title);
    }
    return formatAsSpanHtml("abbr", attr, (String) el.value);
  }

  @SuppressWarnings("unchecked")
  private String convertRoot(Element el, int ind) {
    String result = inner(el, ind);
    if (footnoteLocation != null) {
      result = result.replaceFirst(Pattern.quote(footnoteLocation), Matcher.quoteReplacement(footnoteContent()));
    } else {
      result = result + footnoteContent();
    }
    if (tocCode != null) {
      Element tocTree = generateTocTree(toc, (String) tocCode[0], (Map<String, Object>) tocCode[1]);
      String text = tocTree.children.isEmpty() ? "" : convert(tocTree, 0);
      result = result.replaceFirst(Pattern.quote((String) tocCode[2]), Matcher.quoteReplacement(text));
    }
    return result;
  }

  String formatAsSpanHtml(String name, Map<String, Object> attr, String body) {
    return "<" + name + htmlAttributes(attr) + ">" + body + "</" + name + ">";
  }

  String formatAsBlockHtml(String name, Map<String, Object> attr, String body, int ind) {
    return RubyStr.spaces(ind) + "<" + name + htmlAttributes(attr) + ">" + body + "</" + name + ">\n";
  }

  String formatAsIndentedBlockHtml(String name, Map<String, Object> attr, String body, int ind) {
    return RubyStr.spaces(ind) + "<" + name + htmlAttributes(attr) + ">\n" + body + RubyStr.spaces(ind) + "</" + name + ">\n";
  }

  private static final Pattern LANG_OR_BOL = RubyStr.rx(HtmlConstants.WB + "language-\\S+|(^)");

  private void addSyntaxHighlighterToClassAttr(Map<String, Object> attr, String lang) {
    Object c = attr.get("class");
    String cls = RubyStr.lstrip((c == null ? "" : c.toString()) + highlighterClass);
    if (lang != null) {
      cls = RubyStr.sub(cls, LANG_OR_BOL, m -> "language-" + lang + (m.group(1) != null ? " " : ""));
    }
    attr.put("class", cls);
  }

  @SuppressWarnings("unchecked")
  private Element generateTocTree(List<Object[]> toc, String type, Map<String, Object> attr) {
    Element sections = new Element(type, null, new LinkedHashMap<>(attr), null);
    if (sections.attr().get("id") == null) {
      sections.attr().put("id", "markdown-toc");
    }
    List<Element> stk = new ArrayList<>();
    for (Object[] entry : toc) {
      Object level = entry[0];
      Object id = entry[1];
      List<Element> children = (List<Element>) entry[2];
      Element li = new Element("li", null, null, Element.opts("level", level));
      li.children.add(new Element("p", null, null, Element.opts("transparent", true)));
      Element a = new Element("a", null);
      a.attr().put("href", "#" + id);
      a.attr().put("id", sections.attr().get("id") + "-" + id);
      List<Element> copies = new ArrayList<>();
      for (Element c : children) {
        copies.add(c.deepCopy());
      }
      a.children.addAll(fixForTocEntry(copies));
      li.children.get(li.children.size() - 1).children.add(a);
      li.children.add(new Element(type));

      boolean success = false;
      while (!success) {
        if (stk.isEmpty()) {
          sections.children.add(li);
          stk.add(li);
          success = true;
        } else if (((Number) stk.get(stk.size() - 1).opt("level")).intValue() < ((Number) li.opt("level")).intValue()) {
          Element top = stk.get(stk.size() - 1);
          top.children.get(top.children.size() - 1).children.add(li);
          stk.add(li);
          success = true;
        } else {
          Element item = stk.remove(stk.size() - 1);
          if (item.children.get(item.children.size() - 1).children.isEmpty()) {
            item.children.remove(item.children.size() - 1);
          }
        }
      }
    }
    while (!stk.isEmpty()) {
      Element item = stk.remove(stk.size() - 1);
      if (item.children.get(item.children.size() - 1).children.isEmpty()) {
        item.children.remove(item.children.size() - 1);
      }
    }
    return sections;
  }

  private List<Element> fixForTocEntry(List<Element> elements) {
    removeFootnotes(elements);
    return unwrapLinks(elements);
  }

  private List<Element> unwrapLinks(List<Element> elements) {
    List<Element> out = new ArrayList<>();
    for (Element c : elements) {
      c.children = unwrapLinks(c.children);
      if (c.type.equals("a")) {
        out.addAll(c.children);
      } else {
        out.add(c);
      }
    }
    elements.clear();
    elements.addAll(out);
    return elements;
  }

  private void removeFootnotes(List<Element> elements) {
    elements.removeIf(c -> {
      removeFootnotes(c.children);
      return c.type.equals("footnote");
    });
  }

  private static final String FOOTNOTE_BACKLINK_FMT = "%s<a href=\"#fnref:%s\" class=\"reversefootnote\" role=\"doc-backlink\">%s</a>";

  private String footnoteContent() {
    Element ol = new Element("ol");
    if (footnoteStart != 1) {
      ol.attr().put("start", footnoteStart);
    }
    int i = 0;
    String backlinkText = escapeHtml((String) options.get("footnote_backlink"), "text");
    while (i < footnotes.size()) {
      Object[] f = footnotes.get(i);
      String name = (String) f[0];
      Element data = (Element) f[1];
      int repeat = (Integer) f[3];
      Map<String, Object> la = new LinkedHashMap<>();
      la.put("id", "fn:" + name);
      Element li = new Element("li", null, la, null);
      li.children = new ArrayList<>();
      for (Element c : data.children) {
        li.children.add(c.deepCopy());
      }
      Element para = null;
      boolean insertSpace = false;
      if ((!li.children.isEmpty() && li.children.get(li.children.size() - 1).type.equals("p"))
          || KramdownParser.truthy(options.get("footnote_backlink_inline"))) {
        Element parent = li;
        while (!parent.children.isEmpty() && !isPOrHeader(parent.children.get(parent.children.size() - 1))) {
          parent = parent.children.get(parent.children.size() - 1);
        }
        para = parent.children.isEmpty() ? null : parent.children.get(parent.children.size() - 1);
        insertSpace = true;
      } // 2.6.1: an empty footnote definition (li.children.last&.type) gets a new paragraph below, no error
      if (para == null) {
        para = new Element("p");
        li.children.add(para);
        insertSpace = false;
      }
      if (!((String) options.get("footnote_backlink")).isEmpty()) {
        String nbsp = entityToStr(Entities.entity("nbsp"));
        String value = String.format(FOOTNOTE_BACKLINK_FMT, insertSpace ? nbsp : "", name, backlinkText);
        para.children.add(new Element("raw", value));
        for (int index = 1; index <= repeat; index++) {
          value = String.format(FOOTNOTE_BACKLINK_FMT, nbsp, name + ":" + index,
              backlinkText + "<sup>" + (index + 1) + "</sup>");
          para.children.add(new Element("raw", value));
        }
      }
      ol.children.add(new Element("raw", convert(li, 4)));
      i++;
    }
    if (ol.children.isEmpty()) {
      return "";
    } else {
      Map<String, Object> da = new LinkedHashMap<>();
      da.put("class", "footnotes");
      da.put("role", "doc-endnotes");
      return formatAsIndentedBlockHtml("div", da, convert(ol, 2), 0);
    }
  }

  private static boolean isPOrHeader(Element e) {
    return e.type.equals("p") || e.type.equals("header");
  }
}
