package org.hl7.fhir.sushi.xhtml;

import static org.hl7.fhir.sushi.xhtml.HtmlParser.WS;
import static org.hl7.fhir.sushi.xhtml.HtmlParser.lower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.JsNumber;
import org.hl7.fhir.sushi.xhtml.HtmlParser.Attr;

// port of html-minifier-terser 5.1.1 src/htmlminifier.js (only the subset SUSHI reaches)
/**
 * Minifies assigned xhtml exactly like html-minifier-terser 5.1.1 called as {@code minify(value, {collapseWhitespace:
 * true, html5: false, keepClosingSlash: true})}. Every other option is at its 5.1.1 default, which leaves CSS/JS/URL
 * minification as identity functions, comments and attribute quotes kept, entities not decoded, no optional-tag or
 * empty-element removal; those code paths are not ported. Still reachable and ported: whitespace collapsing (incl. the
 * NBSP rules and inline-tag rules), html5=false auto-closing of inline elements before block elements, the
 * {@code <!-- htmlmin:ignore -->} blocks and the {@code <% %>}/{@code <? ?>} custom fragments (JS replaces them with
 * random-id placeholders; we use a deterministic id not occurring in the input, which yields the same output),
 * attribute value cleaning (class, style, URI, number, srcset, meta viewport/CSP, script type, media) and quote
 * selection, svg case preservation, "Parse Error: ..." and the TypeErrors JS throws on value-less attributes.
 */
public final class HtmlMinifier {
  private HtmlMinifier() {}

  /** The options SUSHI passes; every other option is at its 5.1.1 default. */
  public record Options(boolean collapseWhitespace, boolean html5, boolean keepClosingSlash) {
    public static final Options SUSHI = new Options(true, false, true);
  }

  /** @throws MinifyException with html-minifier's message (e.g. "Parse Error: ...") where JS throws */
  public static String minify(String value, Options options) {
    if (!Options.SUSHI.equals(options)) {
      throw new UnsupportedOperationException("only SUSHI's html-minifier options are ported: " + options);
    }
    return new Minify().run(value);
  }

  /** An Error thrown by html-minifier-terser; getMessage() is the JS message text. */
  public static final class MinifyException extends RuntimeException {
    public MinifyException(String message) { super(message); }
  }

  // ---------------------------------------------------------------- JS regex helpers ($ is always \z here)

  private static String replaceFirst(String s, Pattern p, Function<Matcher, String> fn) {
    Matcher m = p.matcher(s);
    if (!m.find()) return s;
    return s.substring(0, m.start()) + fn.apply(m) + s.substring(m.end());
  }

  private static String replaceAll(String s, Pattern p, Function<Matcher, String> fn) {
    Matcher m = p.matcher(s);
    StringBuilder sb = new StringBuilder();
    int last = 0;
    while (m.find()) {
      sb.append(s, last, m.start()).append(fn.apply(m));
      last = m.end();
      // after an empty match Matcher.find() advances by one, like JS's global replace
    }
    sb.append(s, last, s.length());
    return sb.toString();
  }

  private static boolean test(Pattern p, String s) {
    return p.matcher(s).find();
  }

  private static Pattern re(String s) {
    return Pattern.compile(s);
  }

  private static boolean truthy(String s) {
    return s != null && !s.isEmpty();
  }

  // ---------------------------------------------------------------- whitespace

  private static final Pattern LEAD_SPACES = re("^[ \\n\\r\\t\\f]+");
  private static final Pattern TRAIL_SPACES = re("[ \\n\\r\\t\\f]+\\z");
  private static final Pattern LEAD_WS_NBSP = re("^[ \\n\\r\\t\\f\\u00A0]+");
  private static final Pattern TRAIL_WS_NBSP = re("[ \\n\\r\\t\\f\\u00A0]+\\z");
  private static final Pattern WS_NBSP = re("[ \\n\\r\\t\\f\\u00A0]+");
  private static final Pattern NBSP_RUNS_ALL = re("(^|\\u00A0+)[^\\u00A0]+");
  private static final Pattern LEAD_NON_NBSP = re("^[^\\u00A0]+");
  private static final Pattern NBSP_THEN_OTHER = re("(\\u00A0+)[^\\u00A0]+");
  private static final Pattern OTHER_THEN_NBSP = re("[^\\u00A0]+(\\u00A0+)");
  private static final Pattern TRAIL_NON_NBSP = re("[^\\u00A0]+\\z");

  static String trimWhitespace(String str) {
    if (str == null || str.isEmpty()) return str;
    return replaceFirst(replaceFirst(str, LEAD_SPACES, m -> ""), TRAIL_SPACES, m -> "");
  }

  static String collapseWhitespaceAll(String str) {
    if (str == null || str.isEmpty()) return str;
    return replaceAll(str, WS_NBSP, m -> {
      String spaces = m.group();
      return spaces.equals("\t") ? "\t" : replaceAll(spaces, NBSP_RUNS_ALL, n -> n.group(1) + " ");
    });
  }

  /** collapseWhitespace(str, options, trimLeft, trimRight, collapseAll) with preserveLineBreaks unset */
  static String collapseWhitespace(String str, boolean conservativeCollapse, boolean trimLeft, boolean trimRight,
      boolean collapseAll) {
    if (trimLeft) {
      str = replaceFirst(str, LEAD_WS_NBSP, m -> {
        String spaces = m.group();
        if (conservativeCollapse && spaces.equals("\t")) return "\t";
        String r = replaceAll(replaceFirst(spaces, LEAD_NON_NBSP, n -> ""), NBSP_THEN_OTHER, n -> n.group(1) + " ");
        return !r.isEmpty() ? r : (conservativeCollapse ? " " : "");
      });
    }
    if (trimRight) {
      str = replaceFirst(str, TRAIL_WS_NBSP, m -> {
        String spaces = m.group();
        if (conservativeCollapse && spaces.equals("\t")) return "\t";
        String r = replaceFirst(replaceAll(spaces, OTHER_THEN_NBSP, n -> " " + n.group(1)), TRAIL_NON_NBSP, n -> "");
        return !r.isEmpty() ? r : (conservativeCollapse ? " " : "");
      });
    }
    if (collapseAll) {
      str = collapseWhitespaceAll(str);
    }
    return str;
  }

  // createMapFromString(values) without ignoreCase: case-sensitive
  private static Set<String> map(String values) {
    return Set.of(values.split(","));
  }

  private static final Set<String> INLINE_TAGS = map("a,abbr,acronym,b,bdi,bdo,big,button,cite,code,del,dfn,em,font,i,ins,kbd,label,mark,math,nobr,object,q,rp,rt,rtc,ruby,s,samp,select,small,span,strike,strong,sub,sup,svg,textarea,time,tt,u,var");
  private static final Set<String> INLINE_TEXT_TAGS = map("a,abbr,acronym,b,big,del,em,font,i,ins,kbd,mark,nobr,rp,s,samp,small,span,strike,strong,sub,sup,time,tt,u,var");
  private static final Set<String> SELF_CLOSING_INLINE_TAGS = map("comment,img,input,wbr");

  static String collapseWhitespaceSmart(String str, String prevTag, String nextTag) {
    boolean trimLeft = truthy(prevTag) && !SELF_CLOSING_INLINE_TAGS.contains(prevTag);
    if (trimLeft) {
      trimLeft = prevTag.charAt(0) == '/' ? !INLINE_TAGS.contains(prevTag.substring(1)) : !INLINE_TEXT_TAGS.contains(prevTag);
    }
    boolean trimRight = truthy(nextTag) && !SELF_CLOSING_INLINE_TAGS.contains(nextTag);
    if (trimRight) {
      trimRight = nextTag.charAt(0) == '/' ? !INLINE_TEXT_TAGS.contains(nextTag.substring(1)) : !INLINE_TAGS.contains(nextTag);
    }
    return collapseWhitespace(str, false, trimLeft, trimRight, truthy(prevTag) && truthy(nextTag));
  }

  // ---------------------------------------------------------------- attributes

  private static final String TYPE_ERROR = "Cannot read properties of undefined (reading '%s')";

  private static final Pattern EVENT_ATTR = re("^on[a-z]{3,}\\z");
  private static final Pattern JAVASCRIPT_PREFIX = Pattern.compile("^javascript:[" + WS + "]*", Pattern.CASE_INSENSITIVE);
  private static final Pattern ENDS_SEMI = re(";\\z");
  private static final Pattern ENDS_ENTITY = re("&#?[0-9a-zA-Z]+;\\z");
  private static final Pattern WS_SEMI_END = re("[" + WS + "]*;\\z");
  private static final Pattern SRCSET_SPLIT = re("[" + WS + "]+,[" + WS + "]*|[" + WS + "]*,[" + WS + "]+");
  private static final Pattern SRCSET_DESCRIPTOR = re("[" + WS + "]+([1-9][0-9]*w|[0-9]+(?:\\.[0-9]+)?x)\\z");
  private static final Pattern WS_RUN = re("[" + WS + "]+");
  private static final Pattern DECIMAL = re("[0-9]+\\.[0-9]+");
  private static final Pattern SEMI_WS = re("[" + WS + "]*;[" + WS + "]*");
  private static final Pattern SEMI = re(";");

  private static boolean isUriTypeAttribute(String attrName, String tag) {
    return tag.matches("a|area|link|base") && attrName.equals("href")
        || tag.equals("img") && attrName.matches("src|longdesc|usemap")
        || tag.equals("object") && attrName.matches("classid|codebase|data|usemap")
        || tag.equals("q") && attrName.equals("cite")
        || tag.equals("blockquote") && attrName.equals("cite")
        || (tag.equals("ins") || tag.equals("del")) && attrName.equals("cite")
        || tag.equals("form") && attrName.equals("action")
        || tag.equals("input") && (attrName.equals("src") || attrName.equals("usemap"))
        || tag.equals("head") && attrName.equals("profile")
        || tag.equals("script") && (attrName.equals("src") || attrName.equals("for"));
  }

  private static boolean isNumberTypeAttribute(String attrName, String tag) {
    return tag.matches("a|area|object|button") && attrName.equals("tabindex")
        || tag.equals("input") && (attrName.equals("maxlength") || attrName.equals("tabindex"))
        || tag.equals("select") && (attrName.equals("size") || attrName.equals("tabindex"))
        || tag.equals("textarea") && attrName.matches("rows|cols|tabindex")
        || tag.equals("colgroup") && attrName.equals("span")
        || tag.equals("col") && attrName.equals("span")
        || (tag.equals("th") || tag.equals("td")) && (attrName.equals("rowspan") || attrName.equals("colspan"));
  }

  private static boolean isLinkType(String tag, List<Attr> attrs, String value) {
    if (!tag.equals("link")) return false;
    for (Attr a : attrs) {
      if (a.name.equals("rel") && value.equals(a.value)) return true;
    }
    return false;
  }

  private static final Set<String> EXECUTABLE_SCRIPTS_MIMETYPES = Set.of("text/javascript", "text/ecmascript",
      "text/jscript", "application/javascript", "application/x-javascript", "application/ecmascript", "module");

  private static boolean isScriptTypeAttribute(String attrValue) {
    if (attrValue == null) throw new MinifyException(String.format(TYPE_ERROR, "split"));
    String v = lower(trimWhitespace(SEMI.split(attrValue, -1)[0]));
    return v.isEmpty() || EXECUTABLE_SCRIPTS_MIMETYPES.contains(v);
  }

  private static boolean isExecutableScript(String tag, List<Attr> attrs) {
    if (!tag.equals("script")) return false;
    for (Attr a : attrs) {
      if (lower(a.name).equals("type")) return isScriptTypeAttribute(a.value);
    }
    return true;
  }

  private static boolean isStyleLinkTypeAttribute(String attrValue) {
    if (attrValue == null) throw new MinifyException(String.format(TYPE_ERROR, "toLowerCase"));
    String v = trimWhitespace(attrValue);
    v = lower(v);
    return v.isEmpty() || v.equals("text/css");
  }

  private static boolean isStyleSheet(String tag, List<Attr> attrs) {
    if (!tag.equals("style")) return false;
    for (Attr a : attrs) {
      if (lower(a.name).equals("type")) return isStyleLinkTypeAttribute(a.value);
    }
    return true;
  }

  private static boolean isMediaQuery(String tag, List<Attr> attrs, String attrName) {
    return attrName.equals("media") && (isLinkType(tag, attrs, "stylesheet") || isStyleSheet(tag, attrs));
  }

  private static boolean isSrcset(String attrName, String tag) {
    return attrName.equals("srcset") && (tag.equals("img") || tag.equals("source"));
  }

  private static boolean isMetaViewport(String tag, List<Attr> attrs) {
    if (!tag.equals("meta")) return false;
    for (Attr a : attrs) {
      if (a.name.equals("name") && "viewport".equals(a.value)) return true;
    }
    return false;
  }

  private static boolean isContentSecurityPolicy(String tag, List<Attr> attrs) {
    if (!tag.equals("meta")) return false;
    for (Attr a : attrs) {
      if (lower(a.name).equals("http-equiv")) {
        if (a.value == null) throw new MinifyException(String.format(TYPE_ERROR, "toLowerCase"));
        if (lower(a.value).equals("content-security-policy")) return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------- the minify() run

  private static final class Normalized {
    final Attr attr;
    final String name;
    final String value;

    Normalized(Attr attr, String name, String value) {
      this.attr = attr;
      this.name = name;
      this.value = value;
    }
  }

  private static final class Minify implements HtmlParser.Handler {
    private final List<String> buffer = new ArrayList<>();
    private String charsPrevTag;
    private String currentChars = "";
    private boolean hasChars;
    private String currentTag = "";
    private List<Attr> currentAttrs = List.of();
    private final List<String> stackNoTrimWhitespace = new ArrayList<>();
    private final List<String> stackNoCollapseWhitespace = new ArrayList<>();
    private final List<String> ignoredMarkupChunks = new ArrayList<>();
    private final List<String[]> ignoredCustomMarkupChunks = new ArrayList<>();
    private String uidIgnore;
    private String uidAttr;
    private Pattern uidPattern;
    /** depth of svg elements: inside, options.name is identity instead of toLowerCase */
    private int svgDepth;

    private String name(String n) {
      return svgDepth > 0 ? n : lower(n);
    }

    /** `str.indexOf(uidIgnore)` where an unset uidIgnore is undefined, i.e. searches for "undefined" */
    private boolean containsUidIgnore(String s) {
      return s.contains(uidIgnore != null ? uidIgnore : "undefined");
    }

    private static String uniqueId(String value) {
      // JS: random base-36 letters/digits; any id not occurring in value gives the same output
      for (long n = 0; ; n++) {
        StringBuilder sb = new StringBuilder("zq");
        long k = n;
        do {
          sb.append((char) ('a' + (k % 26)));
          k /= 26;
        } while (k > 0);
        String id = sb.toString();
        if (!value.contains(id)) return id;
      }
    }

    /** the uidPattern replacement the minifyCSS/minifyJS wrappers apply before calling the (identity) minifier */
    private String restoreFragmentSpacing(String text) {
      if (uidPattern == null) return text;
      return replaceAll(text, uidPattern, m -> {
        String[] chunks = ignoredCustomMarkupChunks.get(Integer.parseInt(m.group(2)));
        return chunks[1] + uidAttr + m.group(2) + uidAttr + chunks[2];
      });
    }

    /** options.minifyJS (identity, wrapped when custom fragments were found) */
    private String minifyJS(String text) {
      return restoreFragmentSpacing(text);
    }

    /**
     * options.minifyCSS (identity, wrapped when custom fragments were found). The wrapper also runs clean-css only to
     * protect fragment ids from it and then undoes that protection, which is a no-op around the identity minifier.
     */
    private String minifyCSS(String text) {
      return restoreFragmentSpacing(text);
    }

    String run(String value) {
      value = collapseWhitespace(value, false, true, true, false);

      final String v0 = value;
      value = replaceAll(value, re("<!-- htmlmin:ignore -->([\\s\\S]*?)<!-- htmlmin:ignore -->"), m -> {
        if (uidIgnore == null) uidIgnore = uniqueId(v0);
        String token = "<!--" + uidIgnore + ignoredMarkupChunks.size() + "-->";
        ignoredMarkupChunks.add(m.group(1));
        return token;
      });

      final String v1 = value;
      Pattern reCustomIgnore = re("[" + WS + "]*(?:<%[\\s\\S]*?%>|<\\?[\\s\\S]*?\\?>)+[" + WS + "]*");
      Pattern chunkRe = re("^([" + WS + "]*)[\\s\\S]*?([" + WS + "]*)\\z");
      value = replaceAll(value, reCustomIgnore, m -> {
        if (uidAttr == null) {
          uidAttr = uniqueId(v1);
          uidPattern = re("([" + WS + "]*)" + uidAttr + "([0-9]+)" + uidAttr + "([" + WS + "]*)");
        }
        String token = uidAttr + ignoredCustomMarkupChunks.size() + uidAttr;
        Matcher c = chunkRe.matcher(m.group());
        c.find();
        ignoredCustomMarkupChunks.add(new String[] {c.group(), c.group(1), c.group(2)});
        return "\t" + token + "\t";
      });

      HtmlParser.parse(value, this);

      squashTrailingWhitespace("br");

      String str = String.join("", buffer);
      if (uidPattern != null) {
        str = replaceAll(str, uidPattern, m -> {
          String chunk = ignoredCustomMarkupChunks.get(Integer.parseInt(m.group(2)))[0];
          String prefix = m.group(1), suffix = m.group(3);
          if (!prefix.equals("\t")) chunk = prefix + chunk;
          if (!suffix.equals("\t")) chunk += suffix;
          return collapseWhitespace(chunk, true, test(re("^[ \\n\\r\\t\\f]"), chunk), test(re("[ \\n\\r\\t\\f]\\z"), chunk),
              false);
        });
      }
      if (uidIgnore != null) {
        str = replaceAll(str, re("<!--" + uidIgnore + "([0-9]+)-->"),
            m -> ignoredMarkupChunks.get(Integer.parseInt(m.group(1))));
      }
      return collapseWhitespace(str, false, true, true, false);
    }

    private static final Pattern END_TAG_ITEM = re("^</([\\w:-]+)>\\z");

    private static boolean canCollapseWhitespace(String tag) {
      return !(tag != null && tag.matches("script|style|pre|textarea"));
    }

    private static boolean canTrimWhitespace(String tag) {
      return !(tag != null && tag.matches("pre|textarea"));
    }

    /** look for trailing whitespaces, bypass any inline tags */
    private void trimTrailingWhitespace(int index, String nextTag) {
      for (String endTag = null; index >= 0 && canTrimWhitespace(endTag); index--) {
        String str = buffer.get(index);
        Matcher match = END_TAG_ITEM.matcher(str);
        if (match.find()) {
          endTag = match.group(1);
        } else if (str.endsWith(">")) {
          break;
        } else {
          String r = collapseWhitespaceSmart(str, null, nextTag);
          buffer.set(index, r);
          if (!r.isEmpty()) break;
        }
      }
    }

    private void squashTrailingWhitespace(String nextTag) {
      int charsIndex = buffer.size() - 1;
      if (buffer.size() > 1) {
        String item = buffer.get(buffer.size() - 1);
        if ((item.startsWith("<!") || item.isEmpty()) && !containsUidIgnore(item)) {
          charsIndex--;
        }
      }
      trimTrailingWhitespace(charsIndex, nextTag);
    }

    private Normalized normalizeAttr(Attr attr, List<Attr> attrs, String tag) {
      String attrName = name(attr.name);
      String attrValue = attr.value;
      if (truthy(attrValue)) {
        attrValue = cleanAttributeValue(tag, attrName, attrValue, attrs);
      }
      return new Normalized(attr, attrName, attrValue);
    }

    private String cleanAttributeValue(String tag, String attrName, String attrValue, List<Attr> attrs) {
      if (EVENT_ATTR.matcher(attrName).find()) {
        attrValue = replaceFirst(trimWhitespace(attrValue), JAVASCRIPT_PREFIX, m -> "");
        return minifyJS(attrValue);
      } else if (attrName.equals("class")) {
        attrValue = trimWhitespace(attrValue);
        return collapseWhitespaceAll(attrValue);
      } else if (isUriTypeAttribute(attrName, tag)) {
        return trimWhitespace(attrValue); // minifyURLs is identity (canonical links or not)
      } else if (isNumberTypeAttribute(attrName, tag)) {
        return trimWhitespace(attrValue);
      } else if (attrName.equals("style")) {
        attrValue = trimWhitespace(attrValue);
        if (truthy(attrValue)) {
          if (test(ENDS_SEMI, attrValue) && !test(ENDS_ENTITY, attrValue)) {
            attrValue = replaceFirst(attrValue, WS_SEMI_END, m -> ";");
          }
          attrValue = minifyCSS(attrValue);
        }
        return attrValue;
      } else if (isSrcset(attrName, tag)) {
        String[] candidates = SRCSET_SPLIT.split(trimWhitespace(attrValue), -1);
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
          String url = candidate;
          String descriptor = "";
          Matcher match = SRCSET_DESCRIPTOR.matcher(candidate);
          if (match.find()) {
            url = url.substring(0, url.length() - match.group().length());
            String d = match.group(1);
            double num = Double.parseDouble(d.substring(0, d.length() - 1));
            String suffix = d.substring(d.length() - 1);
            if (num != 1 || !suffix.equals("x")) {
              descriptor = " " + JsNumber.toString(num) + suffix;
            }
          }
          out.add(url + descriptor);
        }
        attrValue = String.join(", ", out);
      } else if (isMetaViewport(tag, attrs) && attrName.equals("content")) {
        attrValue = replaceAll(replaceAll(attrValue, WS_RUN, m -> ""), DECIMAL,
            m -> JsNumber.toString(Double.parseDouble(m.group())));
      } else if (isContentSecurityPolicy(tag, attrs) && lower(attrName).equals("content")) {
        return collapseWhitespaceAll(attrValue);
      } else if (tag.equals("script") && attrName.equals("type")) {
        attrValue = trimWhitespace(replaceAll(attrValue, SEMI_WS, m -> ";"));
      } else if (isMediaQuery(tag, attrs, attrName)) {
        attrValue = trimWhitespace(attrValue);
        return minifyCSS(attrValue);
      }
      return attrValue;
    }

    private static int count(String s, char ch) {
      int n = 0;
      for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ch) n++;
      return n;
    }

    private static String buildAttr(Normalized normalized, boolean isLast) {
      String attrName = normalized.name;
      String attrValue = normalized.value;
      Attr attr = normalized.attr;
      String attrFragment;
      if (attrValue == null) {
        attrFragment = attrName;
        if (!isLast) attrFragment += " ";
      } else {
        int apos = count(attrValue, '\'');
        int quot = count(attrValue, '"');
        String attrQuote = apos < quot ? "'" : "\"";
        if (attrQuote.equals("\"")) attrValue = attrValue.replace("\"", "&#34;");
        else attrValue = attrValue.replace("'", "&#39;");
        String emittedAttrValue = attrQuote + attrValue + attrQuote;
        if (!isLast) emittedAttrValue += " ";
        attrFragment = attrName + attr.customAssign + emittedAttrValue;
      }
      return attr.customOpen + attrFragment + attr.customClose;
    }

    @Override
    public void start(String tag, List<Attr> attrs, boolean unary, String unarySlash, boolean autoGenerated) {
      if (lower(tag).equals("svg")) svgDepth++;
      tag = name(tag);
      currentTag = tag;
      charsPrevTag = tag;
      if (!INLINE_TEXT_TAGS.contains(tag)) currentChars = "";
      hasChars = false;
      currentAttrs = attrs;

      if (stackNoTrimWhitespace.isEmpty()) squashTrailingWhitespace(tag);
      if (!unary) {
        if (!canTrimWhitespace(tag) || !stackNoTrimWhitespace.isEmpty()) stackNoTrimWhitespace.add(tag);
        if (!canCollapseWhitespace(tag) || !stackNoCollapseWhitespace.isEmpty()) stackNoCollapseWhitespace.add(tag);
      }

      boolean hasUnarySlash = truthy(unarySlash); // keepClosingSlash
      buffer.add("<" + tag);
      List<String> parts = new ArrayList<>();
      boolean isLast = true;
      for (int i = attrs.size(); --i >= 0; ) {
        Normalized normalized = normalizeAttr(attrs.get(i), attrs, tag);
        parts.add(0, buildAttr(normalized, isLast));
        isLast = false;
      }
      if (!parts.isEmpty()) {
        buffer.add(" ");
        buffer.addAll(parts);
      }
      int lastIdx = buffer.size() - 1;
      buffer.set(lastIdx, buffer.get(lastIdx) + (hasUnarySlash ? "/" : "") + ">");
      // includeAutoGeneratedTags is true: nothing to remove
    }

    @Override
    public void end(String tag, List<Attr> attrs, boolean autoGenerated) {
      if (lower(tag).equals("svg")) svgDepth--;
      tag = name(tag);
      if (!stackNoTrimWhitespace.isEmpty()) {
        if (tag.equals(stackNoTrimWhitespace.get(stackNoTrimWhitespace.size() - 1))) {
          stackNoTrimWhitespace.remove(stackNoTrimWhitespace.size() - 1);
        }
      } else {
        squashTrailingWhitespace("/" + tag);
      }
      if (!stackNoCollapseWhitespace.isEmpty()
          && tag.equals(stackNoCollapseWhitespace.get(stackNoCollapseWhitespace.size() - 1))) {
        stackNoCollapseWhitespace.remove(stackNoCollapseWhitespace.size() - 1);
      }
      boolean isElementEmpty = false;
      if (tag.equals(currentTag)) {
        currentTag = "";
        isElementEmpty = !hasChars;
      }
      buffer.add("</" + tag + ">");
      charsPrevTag = "/" + tag;
      if (!INLINE_TAGS.contains(tag)) {
        currentChars = "";
      } else if (isElementEmpty) {
        currentChars += "|";
      }
    }

    private static final Pattern TRAILING_WS = re("[" + WS + "]+\\z");
    private static final Pattern LEADING_WS = re("^[" + WS + "]");
    private static final Pattern START_OR_WS_AT_END = re("(?:^|[" + WS + "])\\z");
    private static final Pattern WS_AT_END = re("[" + WS + "]\\z");
    private static final Pattern ALL_WS = re("^[" + WS + "]*\\z");

    @Override
    public void chars(String text, String prevTag, String nextTag) {
      prevTag = "".equals(prevTag) ? "comment" : prevTag;
      nextTag = "".equals(nextTag) ? "comment" : nextTag;
      if (stackNoTrimWhitespace.isEmpty()) {
        if ("comment".equals(prevTag)) {
          if (buffer.isEmpty()) throw new MinifyException(String.format(TYPE_ERROR, "indexOf"));
          String prevComment = buffer.get(buffer.size() - 1);
          if (!containsUidIgnore(prevComment)) {
            if (prevComment.isEmpty()) prevTag = charsPrevTag;
            if (buffer.size() > 1 && (prevComment.isEmpty() || currentChars.endsWith(" "))) {
              int charsIndex = buffer.size() - 2;
              String[] t = {text};
              buffer.set(charsIndex, replaceFirst(buffer.get(charsIndex), TRAILING_WS, m -> {
                t[0] = m.group() + t[0];
                return "";
              }));
              text = t[0];
            }
          }
        }
        if (truthy(prevTag)) {
          if (prevTag.equals("/nobr") || prevTag.equals("wbr")) {
            if (test(LEADING_WS, text)) {
              int tagIndex = buffer.size() - 1;
              while (tagIndex > 0 && buffer.get(tagIndex).lastIndexOf("<" + prevTag) != 0) tagIndex--;
              trimTrailingWhitespace(tagIndex - 1, "br");
            }
          } else if (INLINE_TEXT_TAGS.contains(prevTag.charAt(0) == '/' ? prevTag.substring(1) : prevTag)) {
            text = collapseWhitespace(text, false, test(START_OR_WS_AT_END, currentChars), false, false);
          }
        }
        if (truthy(prevTag) || truthy(nextTag)) {
          text = collapseWhitespaceSmart(text, prevTag, nextTag);
        } else {
          text = collapseWhitespace(text, false, true, true, false);
        }
        if (text.isEmpty() && test(WS_AT_END, currentChars) && truthy(prevTag) && prevTag.charAt(0) == '/') {
          trimTrailingWhitespace(buffer.size() - 1, nextTag);
        }
      }
      if (stackNoCollapseWhitespace.isEmpty() && !"html".equals(nextTag) && !(truthy(prevTag) && truthy(nextTag))) {
        text = collapseWhitespace(text, false, false, false, true);
      }
      if (isExecutableScript(currentTag, currentAttrs)) text = minifyJS(text);
      if (isStyleSheet(currentTag, currentAttrs)) text = minifyCSS(text);
      charsPrevTag = test(ALL_WS, text) ? prevTag : "comment";
      if (uidPattern != null && !stackNoTrimWhitespace.isEmpty()) {
        text = replaceAll(text, uidPattern, m -> ignoredCustomMarkupChunks.get(Integer.parseInt(m.group(2)))[0]);
      }
      currentChars += text;
      if (!text.isEmpty()) hasChars = true;
      buffer.add(text);
    }

    @Override
    public void comment(String text, boolean nonStandard) {
      // conditional comments are kept as is (processConditionalComments unset), as are all others (removeComments)
      String prefix = nonStandard ? "<!" : "<!--";
      String suffix = nonStandard ? ">" : "-->";
      buffer.add(prefix + text + suffix);
    }

    @Override
    public void doctype(String doctype) {
      buffer.add(collapseWhitespaceAll(doctype));
    }
  }
}
