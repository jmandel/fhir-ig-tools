package org.hl7.fhir.sushi.js;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON with exact JavaScript semantics: {@link #parse} is JSON.parse, {@link #stringify} is JSON.stringify(value,
 * null, indent), and {@link #outputJson} is what SUSHI writes to disk via fs-extra {@code outputJSONSync(path, obj,
 * {spaces: 2})} (jsonfile: {@code JSON.stringify(obj, null, 2).replace(/\n/g, EOL) + EOL}, EOL "\n").
 *
 * <h2>The JS value model (shared by every module)</h2>
 * <ul>
 *   <li>object: {@code Map<String,Object>} (usually {@link JsObject}), insertion ordered; enumerate with
 *       {@link Js#ownKeys} (array-index keys first, like JS)</li>
 *   <li>array: {@code List<Object>} (ArrayList)</li>
 *   <li>string: {@code String} (UTF-16, same indices/lengths as JS)</li>
 *   <li>number: {@code Double}. Always Double, never Integer/Long, so {@code equals} matches JS {@code ===}/isEqual
 *       (other {@link Number}s are accepted on output and formatted as JS would format their double value).</li>
 *   <li>boolean: {@code Boolean}; null: {@code null}; undefined: {@link Js#UNDEFINED} (omitted from objects,
 *       null in arrays, exactly as JSON.stringify)</li>
 *   <li>objects with a JS toJSON(): {@link JsonSerializable}</li>
 * </ul>
 */
public final class Json {
  private Json() {}

  // ------------------------------------------------------------------------------------------------ stringify

  /** JSON.stringify(value) (no indentation). Returns null for JS undefined results (undefined, top-level). */
  public static String stringify(Object value) {
    return stringify(value, 0);
  }

  /** JSON.stringify(value, null, indent). Returns null when JS would return undefined. */
  public static String stringify(Object value, int indent) {
    StringBuilder sb = new StringBuilder();
    String gap = " ".repeat(Math.min(10, Math.max(0, indent)));
    return write(sb, value, gap, "") ? sb.toString() : null;
  }

  /** The exact bytes-as-string SUSHI writes for a JSON file (2-space indent, trailing "\n"). */
  public static String outputJson(Object value) {
    return stringify(value, 2) + "\n";
  }

  /** Returns false when the value serializes to JS undefined (caller omits / writes null). */
  private static boolean write(StringBuilder sb, Object v, String gap, String indent) {
    if (v instanceof JsonSerializable js) v = js.toJSON();
    if (v == null) {
      sb.append("null");
      return true;
    }
    if (v == Js.UNDEFINED) return false;
    if (v instanceof String s) {
      quote(sb, s);
      return true;
    }
    if (v instanceof Boolean b) {
      sb.append(b ? "true" : "false");
      return true;
    }
    if (v instanceof BigInteger || v instanceof BigDecimal) {
      // JSON.stringify throws "TypeError: Do not know how to serialize a BigInt"; SUSHI must convert first.
      throw new IllegalArgumentException("JSON.stringify: BigInt value " + v);
    }
    if (v instanceof Number n) {
      double d = n.doubleValue();
      sb.append(Double.isFinite(d) ? JsNumber.toString(d) : "null");
      return true;
    }
    if (v instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) m;
      String inner = indent + gap;
      boolean any = false;
      sb.append('{');
      for (String k : Js.ownKeys(map)) {
        Object child = map.get(k);
        if (child instanceof JsonSerializable js) child = js.toJSON();
        if (child == Js.UNDEFINED) continue;
        sb.append(any ? "," : "");
        if (!gap.isEmpty()) sb.append('\n').append(inner);
        quote(sb, k);
        sb.append(gap.isEmpty() ? ":" : ": ");
        write(sb, child, gap, inner);
        any = true;
      }
      if (any && !gap.isEmpty()) sb.append('\n').append(indent);
      sb.append('}');
      return true;
    }
    if (v instanceof List<?> list) {
      String inner = indent + gap;
      sb.append('[');
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) sb.append(',');
        if (!gap.isEmpty()) sb.append('\n').append(inner);
        if (!write(sb, list.get(i), gap, inner)) sb.append("null");
      }
      if (!list.isEmpty() && !gap.isEmpty()) sb.append('\n').append(indent);
      sb.append(']');
      return true;
    }
    throw new IllegalArgumentException("not in the JS value model: " + v.getClass().getName());
  }

  /** JSON.stringify string quoting (ES2019 well-formed: lone surrogates escaped as lowercase \\udxxx). */
  public static void quote(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            hex(sb, c);
          } else if (Character.isHighSurrogate(c)) {
            if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
              sb.append(c).append(s.charAt(++i));
            } else {
              hex(sb, c);
            }
          } else if (Character.isLowSurrogate(c)) {
            hex(sb, c);
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }

  private static void hex(StringBuilder sb, char c) {
    sb.append("\\u");
    String h = Integer.toHexString(c);
    for (int j = h.length(); j < 4; j++) sb.append('0');
    sb.append(h);
  }

  // ------------------------------------------------------------------------------------------------ parse

  /** JSON.parse(text) into the JS value model. Throws {@link JsonParseException} like JS throws SyntaxError. */
  public static Object parse(String text) {
    Parser p = new Parser(text);
    p.ws();
    Object v = p.value();
    p.ws();
    if (p.i < text.length()) throw p.err("Unexpected non-whitespace character after JSON");
    return v;
  }

  /**
   * fs-extra readJSONSync / jsonfile semantics: strips a leading UTF-8 BOM (U+FEFF) before JSON.parse.
   * (FPL and SUSHI read package and config JSON this way.)
   */
  public static Object parseFile(String text) {
    if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
    return parse(text);
  }

  public static final class JsonParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final int position;

    JsonParseException(String msg, int position) {
      super(msg + " in JSON at position " + position);
      this.position = position;
    }
  }

  private static final class Parser {
    final String s;
    int i;

    Parser(String s) {
      this.s = s;
    }

    JsonParseException err(String msg) {
      return new JsonParseException(msg, i);
    }

    void ws() {
      while (i < s.length()) {
        char c = s.charAt(i);
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
        else break;
      }
    }

    Object value() {
      if (i >= s.length()) throw err("Unexpected end of JSON input");
      char c = s.charAt(i);
      switch (c) {
        case '{': return object();
        case '[': return array();
        case '"': return string();
        case 't': return literal("true", Boolean.TRUE);
        case 'f': return literal("false", Boolean.FALSE);
        case 'n': return literal("null", null);
        default:
          if (c == '-' || (c >= '0' && c <= '9')) return number();
          throw err("Unexpected token '" + c + "'");
      }
    }

    Object literal(String word, Object v) {
      if (!s.startsWith(word, i)) throw err("Unexpected token");
      i += word.length();
      return v;
    }

    Map<String, Object> object() {
      JsObject o = new JsObject();
      i++;
      ws();
      if (i < s.length() && s.charAt(i) == '}') {
        i++;
        return o;
      }
      while (true) {
        ws();
        if (i >= s.length() || s.charAt(i) != '"') throw err("Expected property name");
        String k = string();
        ws();
        if (i >= s.length() || s.charAt(i) != ':') throw err("Expected ':' after property name");
        i++;
        ws();
        // JS: a duplicate key keeps its first position and takes the last value; LinkedHashMap.put does the same.
        o.put(k, value());
        ws();
        if (i >= s.length()) throw err("Unexpected end of JSON input");
        char c = s.charAt(i++);
        if (c == '}') return o;
        if (c != ',') throw err("Expected ',' or '}' after property value");
      }
    }

    List<Object> array() {
      List<Object> a = new ArrayList<>();
      i++;
      ws();
      if (i < s.length() && s.charAt(i) == ']') {
        i++;
        return a;
      }
      while (true) {
        ws();
        a.add(value());
        ws();
        if (i >= s.length()) throw err("Unexpected end of JSON input");
        char c = s.charAt(i++);
        if (c == ']') return a;
        if (c != ',') throw err("Expected ',' or ']' after array element");
      }
    }

    String string() {
      i++;
      StringBuilder sb = null;
      int start = i;
      while (true) {
        if (i >= s.length()) throw err("Unterminated string");
        char c = s.charAt(i);
        if (c == '"') {
          String r = sb == null ? s.substring(start, i) : sb.append(s, start, i).toString();
          i++;
          return r;
        }
        if (c < 0x20) throw err("Bad control character in string literal");
        if (c == '\\') {
          if (sb == null) sb = new StringBuilder();
          sb.append(s, start, i);
          i++;
          if (i >= s.length()) throw err("Unterminated string");
          char e = s.charAt(i++);
          switch (e) {
            case '"' -> sb.append('"');
            case '\\' -> sb.append('\\');
            case '/' -> sb.append('/');
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case 'u' -> {
              if (i + 4 > s.length()) throw err("Bad Unicode escape");
              try {
                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
              } catch (NumberFormatException ex) {
                throw err("Bad Unicode escape");
              }
              i += 4;
            }
            default -> throw err("Bad escaped character");
          }
          start = i;
        } else {
          i++;
        }
      }
    }

    Double number() {
      int start = i;
      if (s.charAt(i) == '-') i++;
      if (i >= s.length()) throw err("No number after minus sign");
      if (s.charAt(i) == '0') i++;
      else if (s.charAt(i) >= '1' && s.charAt(i) <= '9') while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      else throw err("No number after minus sign");
      if (i < s.length() && s.charAt(i) == '.') {
        i++;
        int d = i;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
        if (d == i) throw err("Unterminated fractional number");
      }
      if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
        i++;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
        int d = i;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
        if (d == i) throw err("Exponent part is missing a number");
      }
      // Double.parseDouble is correctly rounded, like JS. Large integers lose precision exactly as in JS.
      return Double.parseDouble(s.substring(start, i));
    }
  }
}
