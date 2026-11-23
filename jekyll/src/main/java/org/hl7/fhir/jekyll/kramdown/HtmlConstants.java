package org.hl7.fhir.jekyll.kramdown;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Port of Kramdown::Parser::Html::Constants (kramdown-2.6.1/lib/kramdown/parser/html.rb) plus the
 * REXML regex fragments kramdown borrows (rexml 3.4.4 BaseParser::UNAME_STR, XMLTokens NAME_*).
 */
final class HtmlConstants {
  private HtmlConstants() {}

  // ---- Ruby character classes (Onigmo, UTF-8 strings) ----
  /** [[:alpha:]] (content of a bracket expression) */
  static final String ALPHA = "\\p{IsAlphabetic}";
  /** [[:alnum:]] (content of a bracket expression) */
  static final String ALNUM = "\\p{IsAlphabetic}\\p{IsDigit}";
  /** \p{Word} (content of a bracket expression) */
  // Ruby 3.4's Onigmo also counts Join_Control (U+200C, U+200D) as Word (Ruby 3.2 didn't); the set equals Ruby 3.4.13's
  // \\p{Word} on all 1,112,064 code points (139,612 of them)
  static final String WORD = "\\p{IsAlphabetic}\\p{M}\\p{Nd}\\p{Pc}\\u200C\\u200D";
  /** Ruby's \b (Unicode word boundary even though \w is ASCII) */
  static final String WB = "(?:(?<=[" + WORD + "])(?![" + WORD + "])|(?<![" + WORD + "])(?=[" + WORD + "]))";
  /** Ruby's \B */
  static final String NWB = "(?:(?<=[" + WORD + "])(?=[" + WORD + "])|(?<![" + WORD + "])(?![" + WORD + "]))";

  /** REXML::Parsers::BaseParser::UNAME_STR */
  static final String NCNAME_STR = "[" + ALPHA + "_][-" + ALNUM + "._]*";
  static final String UNAME_STR = "(?:" + NCNAME_STR + ":)?" + NCNAME_STR;

  /** REXML::XMLTokens::NAME_START_CHAR / NAME_CHAR */
  static final String NAME_START_CHARS = ":A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D"
      + "\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD"
      + "\\x{10000}-\\x{EFFFF}";
  static final String NAME_START_CHAR = "[" + NAME_START_CHARS + "]";
  static final String NAME_CHAR = "[" + NAME_START_CHARS + "\\-\\.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]";

  /** REXML::Parsers::BaseParser::REFERENCE_RE */
  static final String REFERENCE = "&(?:([\\w:][\\-\\w\\.:]*);|#\\d+;|#x[0-9a-fA-F]+;)";

  static final Pattern HTML_DOCTYPE_RE = RubyStr.rxmi("<!DOCTYPE.*?>");
  static final Pattern HTML_COMMENT_RE = RubyStr.rxm("<!--(.*?)-->");
  static final Pattern HTML_INSTRUCTION_RE = RubyStr.rxm("<\\?(.*?)\\?>");
  static final Pattern HTML_CDATA_RE = RubyStr.rxm("<!\\[CDATA\\[(.*?)\\]\\]>");
  static final Pattern HTML_ATTRIBUTE_RE = RubyStr.rxm(
      "\\s*(" + UNAME_STR + ")(?:\\s*=\\s*(?:([" + WORD + "]+)|(\"|')(.*?)\\3))?");
  static final Pattern HTML_TAG_RE = RubyStr.rxm("<((?>" + UNAME_STR + "))\\s*((?>\\s+" + UNAME_STR
      + "(?:\\s*=\\s*(?:[" + WORD + "]+|(\"|').*?\\3))?)*)\\s*(\\/)?>");
  static final Pattern HTML_TAG_CLOSE_RE = RubyStr.rxm("<\\/(" + UNAME_STR + ")\\s*>");
  static final String HTML_ENTITY_STR = "&([\\w:][\\w.:-]*);|&#(\\d+);|&\\#x([0-9a-fA-F]+);";
  static final Pattern HTML_ENTITY_RE = RubyStr.rx(HTML_ENTITY_STR);

  static final List<String> HTML_CONTENT_MODEL_BLOCK = List.of("address", "applet", "article", "aside",
      "blockquote", "body", "dd", "details", "div", "dl", "fieldset", "figure", "figcaption", "footer", "form",
      "header", "hgroup", "iframe", "li", "main", "map", "menu", "nav", "noscript", "object", "section",
      "summary", "td");
  static final List<String> HTML_CONTENT_MODEL_SPAN = List.of("a", "abbr", "acronym", "b", "bdo", "big",
      "button", "cite", "caption", "del", "dfn", "dt", "em", "h1", "h2", "h3", "h4", "h5", "h6", "i", "ins",
      "label", "legend", "optgroup", "p", "q", "rb", "rbc", "rp", "rt", "rtc", "ruby", "select", "small",
      "span", "strong", "sub", "sup", "th", "tt");
  static final List<String> HTML_CONTENT_MODEL_RAW = List.of("script", "style", "math", "option", "textarea",
      "pre", "code", "kbd", "samp", "var");
  static final List<String> HTML_SPAN_ELEMENTS = List.of("a", "abbr", "acronym", "b", "big", "bdo", "br",
      "button", "cite", "code", "del", "dfn", "em", "i", "img", "input", "ins", "kbd", "label", "mark",
      "option", "q", "rb", "rbc", "rp", "rt", "rtc", "ruby", "samp", "select", "small", "span", "strong", "sub",
      "sup", "time", "tt", "u", "var");
  static final List<String> HTML_BLOCK_ELEMENTS = List.of("address", "article", "aside", "applet", "body",
      "blockquote", "caption", "col", "colgroup", "dd", "div", "dl", "dt", "fieldset", "figcaption", "footer",
      "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "html", "head", "iframe", "legend",
      "menu", "li", "main", "map", "nav", "ol", "optgroup", "p", "pre", "section", "summary", "table", "tbody",
      "td", "th", "thead", "tfoot", "tr", "ul");
  static final List<String> HTML_ELEMENTS_WITHOUT_BODY = List.of("area", "base", "br", "col", "command",
      "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr");

  static final Set<String> SPAN_SET = new HashSet<>(HTML_SPAN_ELEMENTS);
  static final Set<String> BLOCK_SET = new HashSet<>(HTML_BLOCK_ELEMENTS);
  static final Set<String> WITHOUT_BODY_SET = new HashSet<>(HTML_ELEMENTS_WITHOUT_BODY);
  private static final Set<String> CM_BLOCK = new HashSet<>(HTML_CONTENT_MODEL_BLOCK);
  private static final Set<String> CM_SPAN = new HashSet<>(HTML_CONTENT_MODEL_SPAN);
  private static final Set<String> HTML_ELEMENT = new HashSet<>();

  static {
    HTML_ELEMENT.addAll(HTML_SPAN_ELEMENTS);
    HTML_ELEMENT.addAll(HTML_BLOCK_ELEMENTS);
    HTML_ELEMENT.addAll(HTML_ELEMENTS_WITHOUT_BODY);
    HTML_ELEMENT.addAll(HTML_CONTENT_MODEL_BLOCK);
    HTML_ELEMENT.addAll(HTML_CONTENT_MODEL_SPAN);
    HTML_ELEMENT.addAll(HTML_CONTENT_MODEL_RAW);
  }

  /** HTML_CONTENT_MODEL[name] (default :raw) */
  static String contentModel(String name) {
    if (CM_BLOCK.contains(name)) {
      return "block";
    }
    if (CM_SPAN.contains(name)) {
      return "span";
    }
    return "raw";
  }

  /** HTML_ELEMENT[name] */
  static boolean htmlElement(String name) {
    return HTML_ELEMENT.contains(name);
  }
}
