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
}
