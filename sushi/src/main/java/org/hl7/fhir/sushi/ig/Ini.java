package org.hl7.fhir.sushi.ig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.Json;

// port of the npm package ini 5.0.0 (lib/ini.js): decode and encode, as SUSHI's IGExporter uses them
/**
 * Values: {@code String}, {@code Boolean} (true for "key" without "=", and the literals true/false), {@code null}
 * (literal null), {@code List<Object>} (key[] arrays) and nested {@code Map} sections. Maps keep insertion order,
 * like the null-prototype objects ini builds (keys are never array indexes in practice).
 */
public final class Ini {
  private Ini() {}

  private static final Pattern LINE = Pattern.compile("^\\[([^\\]]*)\\]\\s*$|^([^=]+)(=(.*))?$",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern COMMENT = Pattern.compile("^\\s*[;#]");
  private static final Pattern BLANK = Pattern.compile("^\\s*$");

  public static Map<String, Object> parse(String str) {
    Map<String, Object> out = new LinkedHashMap<>();
    Map<String, Object> p = out;
    for (String line : str.split("[\\r\\n]+", -1)) {
      if (line.isEmpty() || COMMENT.matcher(line).find() || BLANK.matcher(line).matches()) continue;
      Matcher match = LINE.matcher(line);
      if (!match.find()) continue;
      if (match.group(1) != null) {
        String section = unsafe(match.group(1));
        if (section.equals("__proto__")) {
          p = new LinkedHashMap<>();
          continue;
        }
        Object existing = out.get(section);
        if (!(existing instanceof Map<?, ?>) || !Js.truthy(existing)) {
          if (existing == null || !Js.truthy(existing)) {
            existing = new LinkedHashMap<String, Object>();
            out.put(section, existing);
          }
        }
        if (existing instanceof Map<?, ?>) {
          @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) existing;
          p = m;
        } else {
          // out[section] is a truthy non-object (a top-level key of the same name): JS assigns into it, a no-op
          p = new LinkedHashMap<>();
        }
        continue;
      }
      String keyRaw = unsafe(match.group(2));
      boolean isArray = keyRaw.length() > 2 && keyRaw.endsWith("[]");
      String key = isArray ? keyRaw.substring(0, keyRaw.length() - 2) : keyRaw;
      if (key.equals("__proto__")) continue;
      Object valueRaw = match.group(3) != null ? unsafe(match.group(4)) : Boolean.TRUE;
      Object value = valueRaw;
      if ("true".equals(valueRaw)) value = Boolean.TRUE;
      else if ("false".equals(valueRaw)) value = Boolean.FALSE;
      else if ("null".equals(valueRaw)) value = null;
      if (isArray) {
        if (!p.containsKey(key)) {
          p.put(key, new ArrayList<Object>());
        } else if (!(p.get(key) instanceof List<?>)) {
          List<Object> l = new ArrayList<>();
          l.add(p.get(key));
          p.put(key, l);
        }
      }
      if (p.get(key) instanceof List<?> l) {
        @SuppressWarnings("unchecked") List<Object> ll = (List<Object>) l;
        ll.add(value);
      } else {
        p.put(key, value);
      }
    }

    // {a:{y:1},"a.b":{x:2}} --> {a:{y:1,b:{x:2}}}
    List<String> remove = new ArrayList<>();
    for (String k : new ArrayList<>(out.keySet())) {
      Object v = out.get(k);
      if (!(v instanceof Map<?, ?>)) continue;
      List<String> parts = splitSections(k, ".");
      Map<String, Object> q = out;
      String l = parts.remove(parts.size() - 1);
      String nl = l.replace("\\.", ".");
      for (String part : parts) {
        if (part.equals("__proto__")) continue;
        if (!(q.get(part) instanceof Map<?, ?>)) {
          q.put(part, new LinkedHashMap<String, Object>());
        }
        @SuppressWarnings("unchecked") Map<String, Object> next = (Map<String, Object>) q.get(part);
        q = next;
      }
      if (q == out && nl.equals(l)) continue;
      q.put(nl, v);
      remove.add(k);
    }
    for (String del : remove) out.remove(del);
    return out;
  }

  private static List<String> splitSections(String str, String separator) {
    int lastMatchIndex = 0;
    int lastSeparatorIndex = 0;
    int nextIndex;
    List<String> sections = new ArrayList<>();
    do {
      nextIndex = str.indexOf(separator, lastMatchIndex);
      if (nextIndex != -1) {
        lastMatchIndex = nextIndex + separator.length();
        if (nextIndex > 0 && str.charAt(nextIndex - 1) == '\\') continue;
        sections.add(str.substring(lastSeparatorIndex, nextIndex));
        lastSeparatorIndex = nextIndex + separator.length();
      }
    } while (nextIndex != -1);
    sections.add(str.substring(lastSeparatorIndex));
    return sections;
  }

  /** ini.encode(obj, {section, whitespace}) (no align/sort/newline; platform linux). */
  public static String encode(Map<String, Object> obj, String section, boolean whitespace) {
    String eol = "\n";
    String separator = whitespace ? " = " : "=";
    List<String> children = new ArrayList<>();
    StringBuilder out = new StringBuilder();
    for (String k : obj.keySet()) {
      Object val = obj.get(k);
      if (val == Js.UNDEFINED) val = null;
      if (val instanceof List<?> list && Js.truthy(val)) {
        for (Object item : list) out.append(safe(k + "[]")).append(separator).append(safe(item)).append(eol);
      } else if (val instanceof Map<?, ?>) {
        children.add(k);
      } else {
        out.append(safe(k)).append(separator).append(obj.containsKey(k) && obj.get(k) == Js.UNDEFINED
            ? "undefined" : safe(val)).append(eol);
      }
    }
    if (section != null && out.length() > 0) {
      out.insert(0, "[" + safe(section) + "]" + eol);
    }
    for (String k : children) {
      String nk = String.join("\\.", splitSections(k, "."));
      String sec = (section != null ? section + "." : "") + nk;
      @SuppressWarnings("unchecked") Map<String, Object> child = (Map<String, Object>) obj.get(k);
      String c = encode(child, sec, whitespace);
      if (out.length() > 0 && !c.isEmpty()) out.append(eol);
      out.append(c);
    }
    return out.toString();
  }

  private static boolean isQuoted(String val) {
    return (val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"));
  }

  private static final Pattern UNSAFE_CHARS = Pattern.compile("[=\\r\\n]");

  static String safe(Object v) {
    if (!(v instanceof String val) || UNSAFE_CHARS.matcher(val).find() || val.startsWith("[")
        || (val.length() > 1 && isQuoted(val)) || !val.equals(Js.trim(val))) {
      String s = Json.stringify(v);
      return s == null ? "undefined" : s;
    }
    return val.replace(";", "\\;").replace("#", "\\#");
  }

  static String unsafe(String val) {
    val = Js.trim(val == null ? "" : val);
    if (isQuoted(val)) {
      // remove the single quotes before calling JSON.parse
      if (val.charAt(0) == '\'') val = val.substring(1, val.length() - 1);
      try {
        Object parsed = Json.parse(val);
        return parsed instanceof String s ? s : Js.toStr(parsed);
      } catch (RuntimeException e) {
        // ignore errors
      }
      return val;
    }
    // walk the val to find the first not-escaped ; character
    boolean esc = false;
    StringBuilder unesc = new StringBuilder();
    for (int i = 0; i < val.length(); i++) {
      char c = val.charAt(i);
      if (esc) {
        if ("\\;#".indexOf(c) != -1) unesc.append(c);
        else unesc.append('\\').append(c);
        esc = false;
      } else if (";#".indexOf(c) != -1) {
        break;
      } else if (c == '\\') {
        esc = true;
      } else {
        unesc.append(c);
      }
    }
    if (esc) unesc.append('\\');
    return Js.trim(unesc.toString());
  }
}
