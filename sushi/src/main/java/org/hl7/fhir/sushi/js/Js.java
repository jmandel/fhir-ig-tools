package org.hl7.fhir.sushi.js;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * JS / lodash semantics over the JS value model (see {@link Json}). Port helpers so every module translates the same
 * TS idioms the same way. Add helpers here (coordinated: this package is contract) rather than re-inventing them.
 */
public final class Js {
  private Js() {}

  /** JS {@code undefined} as a stored value (a key present with value undefined). Prefer removing the key. */
  public static final Object UNDEFINED = new Object() {
    @Override public String toString() {
      return "undefined";
    }
  };

  /**
   * Object.keys / Object.entries / for..in order (OrdinaryOwnPropertyKeys): array-index keys (canonical decimal
   * integers 0 .. 2^32-2) ascending, then the other string keys in insertion order.
   */
  public static List<String> ownKeys(Map<String, ?> m) {
    List<String> idx = null;
    List<String> rest = new ArrayList<>(m.size());
    for (String k : m.keySet()) {
      if (isArrayIndex(k)) {
        if (idx == null) idx = new ArrayList<>();
        idx.add(k);
      } else {
        rest.add(k);
      }
    }
    if (idx == null) return rest;
    idx.sort((a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
    idx.addAll(rest);
    return idx;
  }

  public static boolean isArrayIndex(String k) {
    int n = k.length();
    if (n == 0 || n > 10) return false;
    if (n > 1 && k.charAt(0) == '0') return false;
    for (int i = 0; i < n; i++) if (k.charAt(i) < '0' || k.charAt(i) > '9') return false;
    return Long.parseLong(k) <= 4294967294L;
  }

  /** JS truthiness for value-model values. */
  public static boolean truthy(Object v) {
    if (v == null || v == UNDEFINED) return false;
    if (v instanceof Boolean b) return b;
    if (v instanceof Number n) {
      double d = n.doubleValue();
      return d != 0 && !Double.isNaN(d);
    }
    if (v instanceof String s) return !s.isEmpty();
    return true;
  }

  /**
   * lodash isEqual over the value model: deep, numbers by value (-0 == 0, NaN == NaN), object key ORDER ignored,
   * keys with undefined values count (lodash: {a: undefined} is not equal to {}).
   */
  public static boolean isEqual(Object a, Object b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (a instanceof JsonSerializable x && !(b instanceof JsonSerializable)) a = x.toJSON();
    if (b instanceof JsonSerializable y && !(a instanceof JsonSerializable)) b = y.toJSON();
    if (a instanceof Number x && b instanceof Number y) {
      double p = x.doubleValue(), q = y.doubleValue();
      return p == q || (Double.isNaN(p) && Double.isNaN(q));
    }
    if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
      if (x.size() != y.size()) return false;
      for (Map.Entry<?, ?> e : x.entrySet()) {
        if (!y.containsKey(e.getKey()) || !isEqual(e.getValue(), y.get(e.getKey()))) return false;
      }
      return true;
    }
    if (a instanceof List<?> x && b instanceof List<?> y) {
      if (x.size() != y.size()) return false;
      for (int i = 0; i < x.size(); i++) if (!isEqual(x.get(i), y.get(i))) return false;
      return true;
    }
    return a.equals(b);
  }

  /**
   * lodash cloneDeep over the value model (maps become {@link JsObject}, lists ArrayList; strings/numbers/booleans
   * shared; shared sub-structures and cycles preserved as lodash does). Non-model objects must implement
   * {@link DeepCloneable}, else they are shared (document any such case at the call site).
   */
  public static <T> T cloneDeep(T v) {
    return cloneDeep(v, new IdentityHashMap<>());
  }

  /** Implemented by Java classes that TS deep-clones with lodash (e.g. ElementDefinition, fshtypes values). */
  public interface DeepCloneable {
    Object cloneDeep(IdentityHashMap<Object, Object> seen);
  }

  @SuppressWarnings("unchecked")
  public static <T> T cloneDeep(T v, IdentityHashMap<Object, Object> seen) {
    if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean || v == UNDEFINED) return v;
    Object done = seen.get(v);
    if (done != null) return (T) done;
    if (v instanceof DeepCloneable dc) {
      Object c = dc.cloneDeep(seen);
      seen.put(v, c);
      return (T) c;
    }
    if (v instanceof Map<?, ?> m) {
      JsObject c = new JsObject();
      seen.put(v, c);
      for (Map.Entry<?, ?> e : m.entrySet()) c.put((String) e.getKey(), cloneDeep(e.getValue(), seen));
      return (T) c;
    }
    if (v instanceof List<?> l) {
      List<Object> c = new ArrayList<>(l.size());
      seen.put(v, c);
      for (Object o : l) c.add(cloneDeep(o, seen));
      return (T) c;
    }
    return v;
  }

  /** JS whitespace + line terminators (the set of String.prototype.trim and regex \s). */
  public static boolean isJsWhitespace(char c) {
    return c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == ' ' || c == 0xA0 || c == 0x1680
        || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000
        || c == 0xFEFF;
  }

  /** String.prototype.trim (Java strip() uses Character.isWhitespace: differs on U+00A0, U+FEFF, U+001C..1F). */
  public static String trim(String s) {
    int a = 0, b = s.length();
    while (a < b && isJsWhitespace(s.charAt(a))) a++;
    while (b > a && isJsWhitespace(s.charAt(b - 1))) b--;
    return s.substring(a, b);
  }

  /** JS String(v) / template-literal interpolation of a value-model value. */
  public static String toStr(Object v) {
    if (v == null) return "null";
    if (v == UNDEFINED) return "undefined";
    if (v instanceof Number n) return JsNumber.toString(n.doubleValue());
    if (v instanceof List<?> l) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < l.size(); i++) {
        if (i > 0) sb.append(',');
        Object o = l.get(i);
        if (o != null && o != UNDEFINED) sb.append(toStr(o));
      }
      return sb.toString();
    }
    if (v instanceof Map<?, ?>) return "[object Object]";
    return v.toString();
  }

  // ------------------------------------------------------------------------------------------------ lodash (strings)

  /** lodash pad: centre in {@code length} with spaces (left floor, right ceil). Length counts UTF-16 units (lodash
   *  counts code points/combining marks; identical for the BMP text SUSHI pads). */
  public static String pad(String s, int length) {
    int n = s.length();
    if (n >= length) return s;
    int total = length - n, left = total / 2;
    return " ".repeat(left) + s + " ".repeat(total - left);
  }

  public static String padStart(String s, int length) {
    return s.length() >= length ? s : " ".repeat(length - s.length()) + s;
  }

  public static String padEnd(String s, int length) {
    return s.length() >= length ? s : s + " ".repeat(length - s.length());
  }

  /** lodash upperFirst (JS toUpperCase of the first UTF-16 unit, e.g. 'ß' becomes "SS"). */
  public static String upperFirst(String s) {
    if (s == null || s.isEmpty()) return s == null ? "" : s;
    return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
  }

  /** lodash escapeRegExp: escapes ^ $ \ . * + ? ( ) [ ] { } | (result is a valid JS and Java regex). */
  public static String escapeRegExp(String s) {
    return s.replaceAll("[\\\\^$.*+?()\\[\\]{}|]", "\\\\$0");
  }

  /**
   * lodash isEmpty over the value model: null/undefined, "", empty list/map are empty; numbers and booleans are
   * empty too (lodash: isEmpty(5) === true); a Java object (fshtypes etc.) is not.
   */
  public static boolean isEmpty(Object v) {
    if (v == null || v == UNDEFINED || v instanceof Number || v instanceof Boolean) return true;
    if (v instanceof String s) return s.isEmpty();
    if (v instanceof java.util.Collection<?> c) return c.isEmpty();
    if (v instanceof Map<?, ?> m) return m.isEmpty();
    return false;
  }

  /** lodash isMatch(object, source): partial deep comparison. Implementation: {@link Lodash} (WP-7, oracle-tested). */
  public static boolean isMatch(Object object, Object source) {
    return Lodash.isMatch(object, source);
  }

  /** lodash merge(object, source). Implementation: {@link Lodash} (WP-7, oracle-tested). */
  public static <T> T merge(T object, Object source) {
    return Lodash.merge(object, source);
  }

  /** lodash mergeWith(object, source, customizer). Implementation: {@link Lodash}. */
  public static <T> T mergeWith(T object, Object source, Lodash.MergeCustomizer customizer) {
    return Lodash.mergeWith(object, source, customizer);
  }

  /** lodash words(string) with the default pattern (ASCII fast path or lodash's unicode word regex). */
  public static List<String> words(String s) {
    java.util.regex.Matcher m = (HAS_UNICODE_WORD.matcher(s).find() ? UNICODE_WORD : ASCII_WORD).matcher(s);
    List<String> out = new ArrayList<>();
    while (m.find()) out.add(m.group());
    return out;
  }

  private static final java.util.regex.Pattern HAS_UNICODE_WORD =
      java.util.regex.Pattern.compile("[a-z][A-Z]|[A-Z]{2}[a-z]|[0-9][a-zA-Z]|[a-zA-Z][0-9]|[^a-zA-Z0-9 ]");
  private static final java.util.regex.Pattern ASCII_WORD =
      java.util.regex.Pattern.compile("[^\\x00-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\x7f]+");
  private static final java.util.regex.Pattern UNICODE_WORD = unicodeWordPattern();

  /**
   * Transcription of lodash 4.17 _unicodeWords.js. JS (no u flag) matches UTF-16 units, Java matches code points, so
   * the surrogate-unit constructs are rewritten as code-point ranges: astral = lone surrogates + U+10000..U+10FFFF,
   * surrogate pair = U+10000..U+10FFFF, \ud83c[\udffb-\udfff] = U+1F3FB..U+1F3FF, regional = U+1F1E6..U+1F1FF.
   */
  private static java.util.regex.Pattern unicodeWordPattern() {
    String rsAstralRange = "\\ud800-\\udfff\\x{10000}-\\x{10FFFF}", rsComboRange = "\\u0300-\\u036f\\ufe20-\\ufe2f\\u20d0-\\u20ff",
        rsDingbatRange = "\\u2700-\\u27bf", rsLowerRange = "a-z\\xdf-\\xf6\\xf8-\\xff", rsMathOpRange = "\\xac\\xb1\\xd7\\xf7",
        rsNonCharRange = "\\x00-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\xbf", rsPunctuationRange = "\\u2000-\\u206f",
        rsSpaceRange = " \\t\\x0b\\f\\xa0\\ufeff\\n\\r\\u2028\\u2029\\u1680\\u180e\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006\\u2007\\u2008\\u2009\\u200a\\u202f\\u205f\\u3000",
        rsUpperRange = "A-Z\\xc0-\\xd6\\xd8-\\xde", rsVarRange = "\\ufe0e\\ufe0f";
    String rsBreakRange = rsMathOpRange + rsNonCharRange + rsPunctuationRange + rsSpaceRange;
    String rsApos = "['’]", rsBreak = "[" + rsBreakRange + "]", rsCombo = "[" + rsComboRange + "]", rsDigits = "\\d+",
        rsDingbat = "[" + rsDingbatRange + "]", rsLower = "[" + rsLowerRange + "]",
        // JS character class "[^...\\d+...]": inside a class, \d is digits and + is a literal plus
        rsMisc = "[^" + rsAstralRange + rsBreakRange + rsDigits + rsDingbatRange + rsLowerRange + rsUpperRange + "]",
        rsFitz = "[\\x{1F3FB}-\\x{1F3FF}]", rsModifier = "(?:" + rsCombo + "|" + rsFitz + ")",
        rsNonAstral = "[^" + rsAstralRange + "]", rsRegional = "[\\x{1F1E6}-\\x{1F1FF}]{2}",
        rsSurrPair = "[\\x{10000}-\\x{10FFFF}]", rsUpper = "[" + rsUpperRange + "]", rsZWJ = "\\u200d";
    String rsMiscLower = "(?:" + rsLower + "|" + rsMisc + ")", rsMiscUpper = "(?:" + rsUpper + "|" + rsMisc + ")",
        rsOptContrLower = "(?:" + rsApos + "(?:d|ll|m|re|s|t|ve))?", rsOptContrUpper = "(?:" + rsApos + "(?:D|LL|M|RE|S|T|VE))?",
        reOptMod = rsModifier + "?", rsOptVar = "[" + rsVarRange + "]?",
        rsOptJoin = "(?:" + rsZWJ + "(?:" + rsNonAstral + "|" + rsRegional + "|" + rsSurrPair + ")" + rsOptVar + reOptMod + ")*",
        rsOrdLower = "\\d*(?:1st|2nd|3rd|(?![123])\\dth)(?=\\b|[A-Z_])",
        rsOrdUpper = "\\d*(?:1ST|2ND|3RD|(?![123])\\dTH)(?=\\b|[a-z_])",
        rsSeq = rsOptVar + reOptMod + rsOptJoin,
        rsEmoji = "(?:" + rsDingbat + "|" + rsRegional + "|" + rsSurrPair + ")" + rsSeq;
    String re = String.join("|",
        rsUpper + "?" + rsLower + "+" + rsOptContrLower + "(?=" + rsBreak + "|" + rsUpper + "|$)",
        rsMiscUpper + "+" + rsOptContrUpper + "(?=" + rsBreak + "|" + rsUpper + rsMiscLower + "|$)",
        rsUpper + "?" + rsMiscLower + "+" + rsOptContrLower,
        rsUpper + "+" + rsOptContrUpper, rsOrdUpper, rsOrdLower, rsDigits, rsEmoji);
    // JS $ without m = end of input (Java \z); JS \b and \d are ASCII, as in Java by default.
    return java.util.regex.Pattern.compile(re.replace("|$)", "|\\z)"));
  }
}
