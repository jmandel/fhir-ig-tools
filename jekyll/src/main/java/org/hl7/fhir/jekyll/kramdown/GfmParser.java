package org.hl7.fhir.jekyll.kramdown;

import static org.hl7.fhir.jekyll.kramdown.RubyStr.rx;
import static org.hl7.fhir.jekyll.kramdown.RubyStr.rxm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of kramdown-parser-gfm-1.1.0/lib/kramdown/parser/gfm.rb (Kramdown::Parser::GFM). */
public class GfmParser extends KramdownParser {

  private final Map<String, Integer> idCounter = new HashMap<>();
  private final Pattern paragraphEndRe;
  private final String hardLineBreak;

  protected GfmParser(String source, Map<String, Object> opts) {
    super(source, opts);
    options.put("auto_id_stripping", true);
    if (truthy(options.get("hard_wrap"))) {
      spanParsers.remove("line_break");
    }
    List<Object> quirks = castList(options.get("gfm_quirks"));
    if (quirks.contains("no_auto_typographic")) {
      spanParsers.remove("typographic_syms");
    }
    String atxHeaderParser;
    if (quirks.contains("paragraph_end")) {
      atxHeaderParser = "atx_header_gfm_quirk";
      paragraphEndRe = PARAGRAPH_END_GFM;
    } else {
      atxHeaderParser = "atx_header_gfm";
      paragraphEndRe = PARAGRAPH_END;
    }
    replaceParser("codeblock_fenced", "codeblock_fenced_gfm");
    replaceParser("atx_header", atxHeaderParser);
    int i = spanParsers.indexOf("escaped_chars");
    if (i >= 0) {
      spanParsers.set(i, "escaped_chars_gfm");
    }
    spanParsers.add("strikethrough_gfm");
    hardLineBreak = (truthy(options.get("hard_wrap")) ? "" : "\\") + "\n";
  }

  private void replaceParser(String current, String replacement) {
    int i = blockParsers.indexOf(current);
    blockParsers.remove(current);
    // Ruby: insert(nil, x) would raise; the parsers are always present
    blockParsers.add(i, replacement);
  }

  public static Object[] parse(String source, Map<String, Object> options) {
    GfmParser p = new GfmParser(source, options);
    p.parse();
    return new Object[] {p.root, p.warnings};
  }

  @Override
  public void parse() {
    super.parse();
    updateElements(root);
  }

  private void updateElements(Element element) {
    List<Element> out = new ArrayList<>(element.children.size());
    Element lastOrig = element.children.isEmpty() ? null : element.children.get(element.children.size() - 1);
    for (Element child : element.children) {
      if (child.type.equals("text") && ((String) child.value).contains(hardLineBreak)) {
        out.addAll(updateTextType(element, child, lastOrig));
      } else if (child.type.equals("html_element")) {
        out.add(child);
      } else if (child.type.equals("header") && truthy(options.get("auto_ids")) && !child.attr().containsKey("id")) {
        child.attr().put("id", generateGfmHeaderId((String) child.opt("raw_text")));
        out.add(child);
      } else {
        updateElements(child);
        out.add(child);
      }
    }
    element.children = out;
  }

  @Override
  protected void updateRawText(Element item) {
    StringBuilder raw = new StringBuilder();
    appendText(item, raw);
    item.options().put("raw_text", raw.toString());
  }

  private void appendText(Element child, StringBuilder raw) {
    switch (child.type) {
      case "text":
      case "codespan":
      case "math":
        raw.append((String) child.value);
        break;
      case "entity":
        raw.append(((Entities.Entity) child.value).chr());
        break;
      case "smart_quote":
        raw.append(Entities.entity((String) child.value).chr());
        break;
      case "typographic_sym":
        String v = (String) child.value;
        if (v.equals("laquo_space")) {
          raw.append("«\u00a0");
        } else if (v.equals("raquo_space")) {
          raw.append("\u00a0»");
        } else {
          raw.append(Entities.entity(v).chr());
        }
        break;
      default:
        for (Element c : child.children) {
          appendText(c, raw);
        }
    }
  }

  static final Pattern NON_WORD_RE = rx("[^" + HtmlConstants.WORD + "\\- \\t]");

  private String generateGfmHeaderId(String text) {
    String result = RubyStr.downcase(text);
    result = RubyStr.gsub(result, NON_WORD_RE, "");
    result = result.replace(' ', '-').replace('\t', '-');
    int counter = idCounter.getOrDefault(result, -1) + 1;
    idCounter.put(result, counter);
    if (counter > 0) {
      result = result + "-" + counter;
    }
    return options.get("auto_id_prefix") + result;
  }

  static final Pattern ATX_HEADER_START = rx("^(?<level>#{1,6})[\\t ]+(?<contents>.*)\\n");
  private static final Pattern QUIRK_TRAILING_HASHES = rx("[\\t ]#+\\z");

  protected boolean parseAtxHeaderGfmQuirk() {
    String[] ti = parseHeaderContents();
    String text = ti[0];
    Matcher m = QUIRK_TRAILING_HASHES.matcher(text);
    if (m.find()) {
      text = RubyStr.rstrip(text.substring(0, m.start()) + text.substring(m.end()));
    }
    if (text.isEmpty()) {
      return false;
    }
    addHeader(src.group("level").length(), text, ti[1]);
    return true;
  }

  static final Pattern FENCED_CODEBLOCK_START = rx("^[ ]{0,3}[~`]{3,}");
  static final Pattern FENCED_CODEBLOCK_MATCH = rxm(
      "^[ ]{0,3}(([~`]){3,})\\s*?((\\S+?)(?:\\?\\S*)?)?\\s*?\\n(.*?)^[ ]{0,3}\\1\\2*\\s*?\\n");

  @Override
  protected Pattern fencedCodeblockMatch() {
    return FENCED_CODEBLOCK_MATCH;
  }

  static final Pattern STRIKETHROUGH_MATCH = rxm("~~(?!\\s|~).*?[^\\s~]~~");

  protected boolean parseStrikethroughGfm() {
    int lineNumber = src.currentLineNumber();
    src.addPos(src.matchedSize());
    String matched = src.matched();
    Element el = new Element("html_element", "del", new java.util.LinkedHashMap<>(),
        Element.opts("category", "span", "line", lineNumber));
    tree.children.add(el);
    Object[] env = saveEnv();
    resetEnv(new StrScanner(matched.substring(2, matched.length() - 2), lineNumber), null, null, "text");
    parseSpans(el, null, null, textType, null);
    restoreEnv(env);
    return true;
  }

  private static final Pattern TASK_UNCHECKED = rx("\\A\\s*\\[ \\]\\s+");
  private static final Pattern TASK_CHECKED = Pattern.compile("\\A\\s*\\[x\\]\\s+",
      RubyStr.FLAGS | Pattern.CASE_INSENSITIVE);
  private static final String BOX_UNCHECKED = "<input type=\"checkbox\" class=\"task-list-item-checkbox\" disabled=\"disabled\" />";
  private static final String BOX_CHECKED = "<input type=\"checkbox\" class=\"task-list-item-checkbox\" "
      + "disabled=\"disabled\" checked=\"checked\" />";

  @Override
  protected boolean parseList() {
    super.parseList();
    Element currentList = null;
    for (Element e : tree.children) {
      if (e.type.equals("ul") || e.type.equals("ol")) {
        currentList = e;
      }
    }
    boolean isTasklist = false;
    for (Element li : currentList.children) {
      List<Element> listItems = li.children;
      if (listItems.isEmpty() || !listItems.get(0).type.equals("p")) {
        continue;
      }
      Element d = listItems.get(0).children.get(0);
      String descendant = (String) d.value;
      if (descendant == null) {
        throw new IllegalStateException("undefined method `gsub!' for nil");
      }
      boolean checked = false;
      boolean unchecked = false;
      Matcher m = TASK_UNCHECKED.matcher(descendant);
      if (m.find()) {
        descendant = RubyStr.gsub(descendant, TASK_UNCHECKED, BOX_UNCHECKED.replace("\\", "\\\\"));
        checked = true;
      }
      m = TASK_CHECKED.matcher(descendant);
      if (m.find()) {
        descendant = RubyStr.gsub(descendant, TASK_CHECKED, BOX_CHECKED.replace("\\", "\\\\"));
        unchecked = true;
      }
      d.value = descendant;
      isTasklist = isTasklist || checked || unchecked;
      if (isTasklist) {
        li.attr().put("class", "task-list-item");
      }
    }
    if (isTasklist) {
      currentList.attr().put("class", "task-list");
    }
    return true;
  }

  static final Pattern ESCAPED_CHARS_GFM = rx("\\\\([\\\\.*_+`<>()\\[\\]{}#!:\\|\"'\\$=\\-~])");

  static final Pattern PARAGRAPH_END_GFM = rx(RubyStr.emb(LAZY_END) + "|" + RubyStr.emb(LIST_START) + "|"
      + RubyStr.emb(ATX_HEADER_START) + "|" + RubyStr.emb(DEFINITION_LIST_START) + "|" + RubyStr.emb(BLOCKQUOTE_START)
      + "|" + RubyStr.emb(FENCED_CODEBLOCK_START));

  @Override
  protected Pattern paragraphEnd() {
    return paragraphEndRe;
  }

  private List<Element> updateTextType(Element element, Element child, Element lastOrig) {
    List<Element> children = new ArrayList<>();
    List<String> lines = RubyStr.split((String) child.value, hardLineBreak, -1);
    boolean omitTrailingBr = lines.get(lines.size() - 1).isEmpty() && "block".equals(Element.category(element))
        && lastOrig == child;
    Object loc = child.opt("location");
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (loc == null) {
        throw new IllegalStateException("undefined method `+' for nil");
      }
      int l = ((Number) loc).intValue() + index;
      children.add(new Element("text", index > 0 ? "\n" + line : line, null, Element.opts("location", l)));
      if (index < lines.size() - 2 || (index == lines.size() - 2 && !omitTrailingBr)) {
        children.add(new Element("br", null, null, Element.opts("location", l)));
      }
    }
    return children;
  }

  static {
    defineParser("atx_header_gfm", ATX_HEADER_START, null, KramdownParser::parseAtxHeader);
    defineParser("atx_header_gfm_quirk", ATX_HEADER_START, null, p -> ((GfmParser) p).parseAtxHeaderGfmQuirk());
    defineParser("codeblock_fenced_gfm", FENCED_CODEBLOCK_START, null, KramdownParser::parseCodeblockFenced);
    defineParser("strikethrough_gfm", STRIKETHROUGH_MATCH, "~~", p -> ((GfmParser) p).parseStrikethroughGfm());
    defineParser("escaped_chars_gfm", ESCAPED_CHARS_GFM, "\\\\", KramdownParser::parseEscapedChars);
  }
}
