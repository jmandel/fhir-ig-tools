package org.hl7.fhir.jekyll.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer (no dependencies). Values map to Java as:
 * object -> LinkedHashMap&lt;String,Object&gt;, array -> ArrayList&lt;Object&gt;, string -> String,
 * integer -> Long, other numbers -> Double, true/false -> Boolean, null -> null.
 * Integer vs float distinction matters for Liquid semantics (Ruby Integer vs Float).
 */
public final class Json {
  private final String s;
  private int i;

  private final boolean yamlStrings;

  private Json(String s) {
    this(s, false);
  }

  private Json(String s, boolean yamlStrings) {
    this.s = s;
    this.yamlStrings = yamlStrings;
  }

  /**
   * Parses JSON the way Jekyll loads `_data/*.json`: through YAML (SafeYAML/libyaml), which differs
   * from strict JSON only in raw line breaks inside strings (invalid JSON, but YAML double-quoted
   * scalars fold them): raw blanks before a break are dropped, blanks after it skipped, one break
   * becomes a space, each further break a "\n" (LS/PS are kept literally).
   */
  public static Object parseAsYaml(String text) {
    Json p = new Json(text, true);
    return p.parseTop();
  }

  public static Object parse(String text) {
    return new Json(text).parseTop();
  }

  private Object parseTop() {
    Json p = this;
    p.ws();
    if (p.i < p.s.length() && p.s.charAt(p.i) == '﻿') {
      p.i++;
      p.ws();
    }
    Object v = p.value();
    p.ws();
    if (p.i != p.s.length()) {
      throw p.err("trailing content");
    }
    return v;
  }

  private RuntimeException err(String msg) {
    return new IllegalArgumentException("JSON " + msg + " at offset " + i);
  }

  private void ws() {
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        i++;
      } else {
        break;
      }
    }
  }

  private Object value() {
    if (i >= s.length()) {
      throw err("unexpected end");
    }
    char c = s.charAt(i);
    switch (c) {
      case '{':
        return object();
      case '[':
        return array();
      case '"':
        return string();
      case 't':
        expect("true");
        return Boolean.TRUE;
      case 'f':
        expect("false");
        return Boolean.FALSE;
      case 'n':
        expect("null");
        return null;
      default:
        return number();
    }
  }

  private void expect(String word) {
    if (!s.startsWith(word, i)) {
      throw err("expected " + word);
    }
    i += word.length();
  }

  private Map<String, Object> object() {
    Map<String, Object> m = new LinkedHashMap<>();
    i++;
    ws();
    if (s.charAt(i) == '}') {
      i++;
      return m;
    }
    while (true) {
      ws();
      if (s.charAt(i) != '"') {
        throw err("expected key");
      }
      String k = string();
      ws();
      if (s.charAt(i) != ':') {
        throw err("expected ':'");
      }
      i++;
      ws();
      m.put(k, value());
      ws();
      char c = s.charAt(i++);
      if (c == '}') {
        return m;
      }
      if (c != ',') {
        throw err("expected ',' or '}'");
      }
    }
  }

  private List<Object> array() {
    List<Object> l = new ArrayList<>();
    i++;
    ws();
    if (s.charAt(i) == ']') {
      i++;
      return l;
    }
    while (true) {
      ws();
      l.add(value());
      ws();
      char c = s.charAt(i++);
      if (c == ']') {
        return l;
      }
      if (c != ',') {
        throw err("expected ',' or ']'");
      }
    }
  }

  private static boolean isBreak(char c) {
    return c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029';
  }

  /** Reads one line break at i (CRLF/CR/LF/NEL -> "\n"; LS/PS literal) and returns it. */
  private String readBreak() {
    char c = s.charAt(i++);
    if (c == '\r' && i < s.length() && s.charAt(i) == '\n') {
      i++;
    }
    return c == '\u2028' || c == '\u2029' ? String.valueOf(c) : "\n";
  }

  private String string() {
    i++;
    StringBuilder b = new StringBuilder();
    int rawBlanks = 0; // raw (unescaped) spaces/tabs at the end of b
    while (true) {
      char c = s.charAt(i++);
      if (c == '"') {
        return b.toString();
      }
      if (yamlStrings && isBreak(c)) {
        i--;
        b.setLength(b.length() - rawBlanks);
        rawBlanks = 0;
        String leading = readBreak();
        StringBuilder trailing = new StringBuilder();
        while (i < s.length()) {
          char d = s.charAt(i);
          if (d == ' ' || d == '\t') {
            i++;
          } else if (isBreak(d)) {
            trailing.append(readBreak());
          } else {
            break;
          }
        }
        if (leading.equals("\n")) {
          b.append(trailing.length() == 0 ? " " : trailing);
        } else {
          b.append(leading).append(trailing);
        }
        continue;
      }
      if (c != '\\') {
        b.append(c);
        rawBlanks = (c == ' ' || c == '\t') ? rawBlanks + 1 : 0;
        continue;
      }
      rawBlanks = 0;
      char e = s.charAt(i++);
      switch (e) {
        case 'n': b.append('\n'); break;
        case 't': b.append('\t'); break;
        case 'r': b.append('\r'); break;
        case 'b': b.append('\b'); break;
        case 'f': b.append('\f'); break;
        case 'u':
          b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
          i += 4;
          break;
        default: b.append(e);
      }
    }
  }

  private Object number() {
    int start = i;
    boolean isFloat = false;
    while (i < s.length()) {
      char c = s.charAt(i);
      if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
        i++;
      } else if (c == '.' || c == 'e' || c == 'E') {
        isFloat = true;
        i++;
      } else {
        break;
      }
    }
    String n = s.substring(start, i);
    if (n.isEmpty()) {
      throw err("unexpected character '" + s.charAt(i) + "'");
    }
    if (!isFloat) {
      try {
        return Long.parseLong(n);
      } catch (NumberFormatException ex) {
        return Double.parseDouble(n);
      }
    }
    return Double.parseDouble(n);
  }

  public static String write(Object v) {
    StringBuilder b = new StringBuilder();
    write(v, b);
    return b.toString();
  }

  @SuppressWarnings("unchecked")
  private static void write(Object v, StringBuilder b) {
    if (v == null) {
      b.append("null");
    } else if (v instanceof String) {
      quote((String) v, b);
    } else if (v instanceof Boolean || v instanceof Long || v instanceof Integer) {
      b.append(v);
    } else if (v instanceof Number) {
      double d = ((Number) v).doubleValue();
      b.append(d == Math.rint(d) && !Double.isInfinite(d) ? String.valueOf(d) : String.valueOf(d));
    } else if (v instanceof Map) {
      b.append('{');
      boolean first = true;
      for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
        if (!first) {
          b.append(',');
        }
        first = false;
        quote(e.getKey(), b);
        b.append(':');
        write(e.getValue(), b);
      }
      b.append('}');
    } else if (v instanceof List) {
      b.append('[');
      boolean first = true;
      for (Object o : (List<Object>) v) {
        if (!first) {
          b.append(',');
        }
        first = false;
        write(o, b);
      }
      b.append(']');
    } else {
      quote(v.toString(), b);
    }
  }

  private static void quote(String s, StringBuilder b) {
    b.append('"');
    for (int k = 0; k < s.length(); k++) {
      char c = s.charAt(k);
      switch (c) {
        case '"': b.append("\\\""); break;
        case '\\': b.append("\\\\"); break;
        case '\n': b.append("\\n"); break;
        case '\r': b.append("\\r"); break;
        case '\t': b.append("\\t"); break;
        default:
          if (c < 0x20) {
            b.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
          } else {
            b.append(c);
          }
      }
    }
    b.append('"');
  }
}
