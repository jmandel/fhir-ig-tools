package org.hl7.fhir.sushi.importer.config;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsNumber;
import org.hl7.fhir.sushi.js.JsObject;

/**
 * The JS operations importConfiguration performs on the untyped parsed YAML, with JS semantics, so malformed configs
 * behave as in TS (including the TypeErrors TS crashes with). Inside this package a missing property reads as
 * {@link Js#UNDEFINED} and a YAML null as Java {@code null}, so TypeError messages can say "of null" or "of
 * undefined" like V8.
 */
final class JsOps {
  private JsOps() {}

  static final Object U = Js.UNDEFINED;

  /** A V8 TypeError (uncaught in TS: app.ts reports "An unexpected error occurred: " + message). */
  static final class JsTypeError extends RuntimeException {
    JsTypeError(String message) {
      super(message);
    }
  }

  static boolean nullish(Object v) {
    return v == null || v == U;
  }

  /** {@code base[key]} (property read); TypeError on a null/undefined base. */
  static Object get(Object base, String key) {
    if (base == null || base == U) {
      throw new JsTypeError(
          "Cannot read properties of " + (base == null ? "null" : "undefined") + " (reading '" + key + "')");
    }
    if (base instanceof Map<?, ?> m) {
      return m.containsKey(key) ? m.get(key) : U;
    }
    if (base instanceof List<?> l) {
      if (key.equals("length")) return (double) l.size();
      int i = index(key);
      return i >= 0 && i < l.size() ? l.get(i) : U;
    }
    if (base instanceof String s) {
      if (key.equals("length")) return (double) s.length();
      int i = index(key);
      return i >= 0 && i < s.length() ? String.valueOf(s.charAt(i)) : U;
    }
    return U;
  }

  /** {@code base?.[key]} */
  static Object getOpt(Object base, String key) {
    return nullish(base) ? U : get(base, key);
  }

  private static int index(String key) {
    if (!Js.isArrayIndex(key)) return -1;
    try {
      return Integer.parseInt(key);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** {@code Object.entries(v)} (Object.keys order): maps, arrays (indices), strings (UTF-16 units); else none. */
  static List<Map.Entry<String, Object>> entries(Object v) {
    if (nullish(v)) throw new JsTypeError("Cannot convert undefined or null to object");
    List<Map.Entry<String, Object>> out = new ArrayList<>();
    if (v instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) m;
      for (String k : Js.ownKeys(mm)) out.add(new SimpleImmutableEntry<>(k, mm.get(k)));
    } else if (v instanceof List<?> l) {
      for (int i = 0; i < l.size(); i++) out.add(new SimpleImmutableEntry<>(String.valueOf(i), l.get(i)));
    } else if (v instanceof String s) {
      for (int i = 0; i < s.length(); i++) out.add(new SimpleImmutableEntry<>(String.valueOf(i), String.valueOf(s.charAt(i))));
    }
    return out;
  }

  /** {@code {...src}} into {@code target} (own enumerable props; primitives other than strings add nothing). */
  static void spread(JsObject target, Object src) {
    if (nullish(src)) return;
    for (Map.Entry<String, Object> e : entries(src)) target.put(e.getKey(), e.getValue());
  }

  /** {@code typeof v === 'object'} (null included, like JS). */
  static boolean isObjectType(Object v) {
    return v == null || v instanceof Map<?, ?> || v instanceof List<?>;
  }

  static boolean isArray(Object v) {
    return v instanceof List<?>;
  }

  /** {@code `${v}`} */
  static String templ(Object v) {
    if (v == null) return "null";
    if (v == U) return "undefined";
    if (v instanceof Double d) return JsNumber.toString(d);
    return Js.toStr(v);
  }

  /** JS truthiness, with undefined. */
  static boolean truthy(Object v) {
    return v != U && Js.truthy(v);
  }

  /** {@code a ?? b} */
  static Object coalesce(Object a, Object b) {
    return nullish(a) ? b : a;
  }

  /** {@code x?.startsWith(...)}-style call on a value that must be a string: V8's TypeError otherwise. */
  static String requireStringForCall(Object v, String expr) {
    if (v instanceof String s) return s;
    throw new JsTypeError(expr + " is not a function");
  }

  /** {@code obj.prop = value} on a primitive in strict mode. */
  static JsTypeError cannotCreateProperty(String prop, Object primitive) {
    String type = primitive instanceof String ? "string" : primitive instanceof Double ? "number" : "boolean";
    return new JsTypeError("Cannot create property '" + prop + "' on " + type + " '" + templ(primitive) + "'");
  }

  /** {@code typeof v} for YAML values. */
  static String typeOf(Object v) {
    if (v == U) return "undefined";
    if (v == null || v instanceof Map<?, ?> || v instanceof List<?>) return "object";
    if (v instanceof String) return "string";
    if (v instanceof Double) return "number";
    if (v instanceof Boolean) return "boolean";
    return "object";
  }

  /** delete every key whose value is undefined (TS removeUndefinedValues). */
  static JsObject removeUndefinedValues(JsObject o) {
    o.values().removeIf(v -> v == U);
    return o;
  }
}
