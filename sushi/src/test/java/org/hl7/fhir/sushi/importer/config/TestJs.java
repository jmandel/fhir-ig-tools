package org.hl7.fhir.sushi.importer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;

/**
 * Test helpers for porting Jest tests that work on plain JS values: {@link #js} parses a JS object literal as written
 * in the TS tests (unquoted keys, single quotes, trailing commas, comments, {@code undefined}, identifiers bound in
 * {@code vars}) into the JS value model, and {@link #assertToEqual} is Jest's {@code toEqual} (key order ignored,
 * undefined-valued properties ignored).
 */
public final class TestJs {
  private TestJs() {}

  public static Object js(String literal) {
    return js(literal, Map.of());
  }

  public static Object js(String literal, Map<String, Object> vars) {
    LiteralParser p = new LiteralParser(literal, vars);
    Object v = p.value();
    p.ws();
    if (p.i != literal.length()) throw new IllegalArgumentException("trailing input at " + p.i + ": " + literal);
    return v;
  }

  public static JsObject obj(String literal) {
    return (JsObject) js(literal);
  }

  /** Jest toEqual. */
  public static void assertToEqual(Object expected, Object actual) {
    if (!toEqual(expected, actual)) {
      assertEquals(Json.stringify(strip(expected), 2), Json.stringify(strip(actual), 2));
      fail("not toEqual (differs only in ways JSON does not show): " + expected + " vs " + actual);
    }
  }

  public static boolean toEqual(Object a, Object b) {
    if (a == Js.UNDEFINED) a = null;
    if (b == Js.UNDEFINED) b = null;
    if (a == null || b == null) return a == b;
    if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
      for (Map.Entry<?, ?> e : ma.entrySet()) {
        if (e.getValue() == Js.UNDEFINED) continue;
        if (!mb.containsKey(e.getKey()) || !toEqual(e.getValue(), mb.get(e.getKey()))) return false;
      }
      for (Map.Entry<?, ?> e : mb.entrySet()) {
        if (e.getValue() == Js.UNDEFINED) continue;
        if (!ma.containsKey(e.getKey()) || ma.get(e.getKey()) == Js.UNDEFINED) return false;
      }
      return true;
    }
    if (a instanceof List<?> la && b instanceof List<?> lb) {
      if (la.size() != lb.size()) return false;
      for (int i = 0; i < la.size(); i++) if (!toEqual(la.get(i), lb.get(i))) return false;
      return true;
    }
    if (a instanceof Number na && b instanceof Number nb) return Double.valueOf(na.doubleValue()).equals(nb.doubleValue());
    return a.equals(b);
  }

  private static Object strip(Object v) {
    if (v instanceof Map<?, ?> m) {
      JsObject o = new JsObject();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (e.getValue() != Js.UNDEFINED) o.put((String) e.getKey(), strip(e.getValue()));
      }
      return o;
    }
    if (v instanceof List<?> l) {
      List<Object> out = new ArrayList<>();
      for (Object o : l) out.add(strip(o));
      return out;
    }
    return v == Js.UNDEFINED ? null : v;
  }

  /** Jest toMatch: a string expectation is a substring, a regex is searched (JS RegExp.test). */
  public static void assertMatch(String expectedSubstring, String actual) {
    assertNotNull(actual, "no message (expected to contain: " + expectedSubstring + ")");
    assertTrue(actual.contains(expectedSubstring), "expected <" + actual + "> to contain <" + expectedSubstring + ">");
  }

  public static void assertMatch(Pattern expected, String actual) {
    assertNotNull(actual, "no message (expected to match: " + expected + ")");
    assertTrue(expected.matcher(actual).find(), "expected <" + actual + "> to match /" + expected + "/");
  }

  /** A JS regex literal body as a Java pattern ({@code \s} widened to JS whitespace). */
  public static Pattern re(String jsRegex) {
    return re(jsRegex, "");
  }

  public static Pattern re(String jsRegex, String flags) {
    String j = jsRegex.replace("\\s", "[\\s\\u00A0\\uFEFF\\u2028\\u2029]");
    int f = (flags.contains("i") ? Pattern.CASE_INSENSITIVE : 0) | (flags.contains("s") ? Pattern.DOTALL : 0)
        | (flags.contains("m") ? Pattern.MULTILINE : 0);
    return Pattern.compile(j, f);
  }

  /** Property path read on JS values: {@code prop(config, "contact", 0, "name")}; missing gives undefined. */
  public static Object prop(Object v, Object... path) {
    for (Object k : path) {
      if (v instanceof Map<?, ?> m) {
        v = m.containsKey(k.toString()) ? m.get(k.toString()) : Js.UNDEFINED;
      } else if (v instanceof List<?> l && k instanceof Integer i) {
        v = i < l.size() ? l.get(i) : Js.UNDEFINED;
      } else if (v instanceof List<?> l && k.equals("length")) {
        v = (double) l.size();
      } else {
        throw new AssertionError("cannot read " + k + " of " + v);
      }
    }
    return v;
  }

  // ---------------------------------------------------------------- literal parser

  private static final class LiteralParser {
    final String s;
    final Map<String, Object> vars;
    int i;

    LiteralParser(String s, Map<String, Object> vars) {
      this.s = s;
      this.vars = vars;
    }

    void ws() {
      while (i < s.length()) {
        char c = s.charAt(i);
        if (Character.isWhitespace(c)) {
          i++;
        } else if (s.startsWith("//", i)) {
          while (i < s.length() && s.charAt(i) != '\n') i++;
        } else if (s.startsWith("/*", i)) {
          int e = s.indexOf("*/", i + 2);
          i = e < 0 ? s.length() : e + 2;
        } else {
          break;
        }
      }
    }

    Object value() {
      ws();
      char c = s.charAt(i);
      if (c == '{') return object();
      if (c == '[') return array();
      if (c == '\'' || c == '"') return string();
      if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) return number();
      String id = ident();
      return switch (id) {
        case "true" -> true;
        case "false" -> false;
        case "null" -> null;
        case "undefined" -> Js.UNDEFINED;
        case "NaN" -> Double.NaN;
        case "Infinity" -> Double.POSITIVE_INFINITY;
        default -> {
          if (!vars.containsKey(id)) throw new IllegalArgumentException("unbound identifier " + id + " at " + i);
          yield vars.get(id);
        }
      };
    }

    JsObject object() {
      JsObject o = new JsObject();
      i++;
      while (true) {
        ws();
        if (s.charAt(i) == '}') {
          i++;
          return o;
        }
        char c = s.charAt(i);
        String key = c == '\'' || c == '"' ? string() : ident();
        ws();
        if (s.charAt(i) != ':') throw new IllegalArgumentException("expected : at " + i + " in " + s);
        i++;
        o.put(key, value());
        ws();
        if (s.charAt(i) == ',') i++;
      }
    }

    List<Object> array() {
      List<Object> l = new ArrayList<>();
      i++;
      while (true) {
        ws();
        if (s.charAt(i) == ']') {
          i++;
          return l;
        }
        l.add(value());
        ws();
        if (s.charAt(i) == ',') i++;
      }
    }

    String ident() {
      int st = i;
      while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '$')) i++;
      if (st == i) throw new IllegalArgumentException("unexpected '" + s.charAt(i) + "' at " + i + " in " + s);
      return s.substring(st, i);
    }

    Double number() {
      int st = i;
      while (i < s.length() && "+-.0123456789eE".indexOf(s.charAt(i)) >= 0) i++;
      return Double.parseDouble(s.substring(st, i));
    }

    String string() {
      char q = s.charAt(i++);
      StringBuilder sb = new StringBuilder();
      while (true) {
        char c = s.charAt(i++);
        if (c == q) return sb.toString();
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        char e = s.charAt(i++);
        switch (e) {
          case 'n' -> sb.append('\n');
          case 't' -> sb.append('\t');
          case 'r' -> sb.append('\r');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'v' -> sb.append('\u000B');
          case '0' -> sb.append('\0');
          case 'u' -> {
            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
            i += 4;
          }
          case 'x' -> {
            sb.append((char) Integer.parseInt(s.substring(i, i + 2), 16));
            i += 2;
          }
          default -> sb.append(e);
        }
      }
    }
  }
}
