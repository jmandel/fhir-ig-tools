package org.hl7.fhir.jekyll.kramdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal stand-in for YAML.safe_load as used by kramdown's simple_hash_validator (option values
 * given as strings, e.g. via {@code {::options typographic_symbols="{hellip: ...}" /}}). Supports
 * flow mappings/sequences, single-line block mappings ({@code key: value} per line) and scalars.
 * Deliberate approximation: not a full YAML parser.
 */
final class MiniYaml {
  private final String s;
  private int i;

  private MiniYaml(String s) {
    this.s = s;
  }

  static Object parse(String text) {
    String t = text.trim();
    if (t.isEmpty()) {
      return null;
    }
    if (t.startsWith("{") || t.startsWith("[")) {
      MiniYaml y = new MiniYaml(t);
      Object v = y.flow();
      y.ws();
      if (y.i != t.length()) {
        throw new IllegalArgumentException("trailing");
      }
      return v;
    }
    if (t.contains(": ") || t.endsWith(":")) {
      Map<Object, Object> m = new LinkedHashMap<>();
      for (String line : t.split("\n")) {
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
          continue;
        }
        int c = line.indexOf(':');
        if (c < 0) {
          throw new IllegalArgumentException("bad mapping");
        }
        m.put(scalar(line.substring(0, c).trim()), scalar(line.substring(c + 1).trim()));
      }
      return m;
    }
    return scalar(t);
  }

  private void ws() {
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
  }

  private Object flow() {
    ws();
    char c = s.charAt(i);
    if (c == '{') {
      i++;
      Map<Object, Object> m = new LinkedHashMap<>();
      ws();
      if (s.charAt(i) == '}') {
        i++;
        return m;
      }
      while (true) {
        Object k = flowScalar(":,}");
        ws();
        Object v = null;
        if (s.charAt(i) == ':') {
          i++;
          ws();
          v = (s.charAt(i) == '{' || s.charAt(i) == '[') ? flow() : flowScalar(",}");
        }
        m.put(k, v);
        ws();
        if (s.charAt(i) == ',') {
          i++;
          continue;
        }
        if (s.charAt(i) == '}') {
          i++;
          return m;
        }
        throw new IllegalArgumentException("bad flow mapping");
      }
    } else if (c == '[') {
      i++;
      List<Object> l = new ArrayList<>();
      ws();
      if (s.charAt(i) == ']') {
        i++;
        return l;
      }
      while (true) {
        ws();
        l.add((s.charAt(i) == '{' || s.charAt(i) == '[') ? flow() : flowScalar(",]"));
        ws();
        if (s.charAt(i) == ',') {
          i++;
          continue;
        }
        if (s.charAt(i) == ']') {
          i++;
          return l;
        }
        throw new IllegalArgumentException("bad flow sequence");
      }
    }
    return flowScalar(",}]");
  }

  private Object flowScalar(String stops) {
    ws();
    char c = s.charAt(i);
    if (c == '"' || c == '\'') {
      int st = ++i;
      StringBuilder b = new StringBuilder();
      while (s.charAt(i) != c) {
        if (c == '"' && s.charAt(i) == '\\') {
          i++;
        }
        b.append(s.charAt(i++));
      }
      i++;
      return b.toString();
    }
    int st = i;
    while (i < s.length() && stops.indexOf(s.charAt(i)) < 0) {
      char ch = s.charAt(i);
      if (ch == '[' || ch == ']' || ch == '{' || ch == '}') {
        throw new IllegalArgumentException("flow indicator in plain scalar");
      }
      if (ch == ':' && i + 1 < s.length() && (Character.isWhitespace(s.charAt(i + 1)) || ",[]{}".indexOf(s.charAt(i + 1)) >= 0)) {
        if (stops.indexOf(':') >= 0) {
          break;
        }
        throw new IllegalArgumentException("mapping values are not allowed in this context");
      }
      i++;
    }
    return scalar(s.substring(st, i).trim());
  }

  private static Object scalar(String t) {
    if ((t.startsWith("'") && t.endsWith("'") && t.length() >= 2) || (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2)) {
      return t.substring(1, t.length() - 1);
    }
    if (t.equals("~") || t.equals("null") || t.isEmpty()) {
      return null;
    }
    if (t.equals("true")) {
      return Boolean.TRUE;
    }
    if (t.equals("false")) {
      return Boolean.FALSE;
    }
    if (t.matches("[-+]?\\d+")) {
      return Long.parseLong(t);
    }
    return t;
  }
}
