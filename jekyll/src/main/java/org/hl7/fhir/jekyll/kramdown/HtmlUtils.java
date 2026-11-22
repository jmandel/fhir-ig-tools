package org.hl7.fhir.jekyll.kramdown;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of kramdown-2.5.2/lib/kramdown/utils/html.rb (Kramdown::Utils::Html). */
final class HtmlUtils {
  private HtmlUtils() {}

  private static final Pattern ESCAPE_ALL_RE = RubyStr.rx("<|>|&");
  private static final Pattern ESCAPE_TEXT_RE = RubyStr.rx("(?:" + HtmlConstants.REFERENCE + ")|(?:<|>|&)");
  private static final Pattern ESCAPE_ATTRIBUTE_RE = RubyStr.rx("(?:" + HtmlConstants.REFERENCE + ")|(?:<|>|&|\")");
  private static final Pattern NEEDS_ESCAPE = RubyStr.rx("[<>&\"]");

  static String escapeMap(String m) {
    switch (m) {
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case "&":
        return "&amp;";
      case "\"":
        return "&quot;";
      default:
        return null;
    }
  }

  static boolean inEscapeMap(String c) {
    return c != null && (c.equals("<") || c.equals(">") || c.equals("&") || c.equals("\""));
  }

  /** escape_html(str, type = :all) with type "all" | "text" | "attribute" */
  static String escapeHtml(String str, String type) {
    if (!NEEDS_ESCAPE.matcher(str).find()) {
      return str;
    }
    Pattern p = type.equals("all") ? ESCAPE_ALL_RE : type.equals("text") ? ESCAPE_TEXT_RE : ESCAPE_ATTRIBUTE_RE;
    Matcher m = p.matcher(str);
    StringBuilder b = new StringBuilder(str.length() + 16);
    int last = 0;
    while (m.find()) {
      b.append(str, last, m.start());
      String g = m.group();
      String r = escapeMap(g);
      b.append(r != null ? r : g);
      last = m.end();
    }
    b.append(str, last, str.length());
    return b.toString();
  }

  /** html_attributes(attr) */
  static String htmlAttributes(Map<String, Object> attr) {
    if (attr == null || attr.isEmpty()) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    for (Map.Entry<String, Object> e : attr.entrySet()) {
      Object v = e.getValue();
      String k = e.getKey();
      if (v == null || (k.equals("id") && RubyStr.strip(v.toString()).isEmpty())) {
        continue;
      }
      b.append(' ').append(k).append("=\"").append(escapeHtml(v.toString(), "attribute")).append('"');
    }
    return b.toString();
  }

  /** entity_to_str(e, original = nil) */
  static String entityToStr(Entities.Entity e, String original, String entityOutput) {
    String c = e.chr();
    if ("as_char".equals(entityOutput) && c != null && (c.equals("\"") || !inEscapeMap(c))) {
      return c;
    } else if (("as_input".equals(entityOutput) || "as_char".equals(entityOutput)) && original != null) {
      return original;
    } else if (("symbolic".equals(entityOutput) || inEscapeMap(c)) && e.name != null) {
      return "&" + e.name + ";";
    } else {
      return "&#" + e.codePoint + ";";
    }
  }

  private static final Pattern REDUNDANT_LINE_BREAK_REGEX = RubyStr.rx(
      "([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]+)\\n([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]+)");

  static String fixCjkLineBreak(String str) {
    while (true) {
      String n = RubyStr.gsub(str, REDUNDANT_LINE_BREAK_REGEX, "\\1\\2");
      if (n.equals(str)) {
        return str;
      }
      str = n;
    }
  }
}
