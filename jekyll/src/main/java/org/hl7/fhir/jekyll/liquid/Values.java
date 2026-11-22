package org.hl7.fhir.jekyll.liquid;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ruby value semantics for the shared value model (README "Value model"): {@code to_s},
 * {@code inspect}, truthiness, {@code ==}, {@code eql?}/{@code hash}, {@code <=>}, {@code to_i},
 * {@code obj[key]}, {@code to_liquid}. Integer = Long (BigInteger on overflow), Float = Double,
 * Array = List, Hash = Map, Range = {@link RubyRange}, Time = {@link RubyTime}.
 *
 * <p>Not a port of one Ruby file: it collects the core-class behaviour Liquid relies on
 * (liquid/extensions.rb plus Ruby's String/Integer/Float/Array/Hash).
 */
public final class Values {
  private Values() {}

  // ---------------------------------------------------------------- type predicates

  public static boolean isInteger(Object o) {
    return o instanceof Long || o instanceof Integer || o instanceof BigInteger || o instanceof Short || o instanceof Byte;
  }

  public static boolean isFloat(Object o) {
    return o instanceof Double || o instanceof Float;
  }

  public static boolean isNumeric(Object o) {
    return isInteger(o) || isFloat(o) || o instanceof BigDecimal;
  }

  /** Normalizes Integer/Short/Byte to Long, BigInteger that fits to Long. */
  public static Object normInt(Object o) {
    if (o instanceof Integer || o instanceof Short || o instanceof Byte) {
      return ((Number) o).longValue();
    }
    if (o instanceof BigInteger) {
      BigInteger b = (BigInteger) o;
      if (b.bitLength() < 64) {
        return b.longValue();
      }
    }
    return o;
  }

  public static BigInteger toBigInteger(Object o) {
    if (o instanceof BigInteger) {
      return (BigInteger) o;
    }
    return BigInteger.valueOf(((Number) o).longValue());
  }

  public static boolean truthy(Object o) {
    return o != null && o != Boolean.FALSE && !(o instanceof Boolean && !((Boolean) o));
  }

  /** Ruby {@code obj.class.name}. */
  public static String className(Object o) {
    if (o == null) {
      return "NilClass";
    }
    if (o instanceof String) {
      return "String";
    }
    if (isInteger(o)) {
      return "Integer";
    }
    if (isFloat(o)) {
      return "Float";
    }
    if (o instanceof BigDecimal) {
      return "BigDecimal";
    }
    if (o instanceof Boolean) {
      return ((Boolean) o) ? "TrueClass" : "FalseClass";
    }
    if (o instanceof List) {
      return "Array";
    }
    if (o instanceof Map) {
      return "Hash";
    }
    if (o instanceof RubyRange) {
      return "Range";
    }
    if (o instanceof RubyTime) {
      return "Time";
    }
    if (o instanceof Drop) {
      return ((Drop) o).rubyClassName();
    }
    if (o instanceof Expression.MethodLiteral) {
      return "Liquid::Expression::MethodLiteral";
    }
    return o.getClass().getSimpleName();
  }

  // ---------------------------------------------------------------- to_liquid

  public static Object toLiquid(Object o) {
    if (o instanceof Drop) {
      return ((Drop) o).toLiquid();
    }
    if (o instanceof Integer || o instanceof Short || o instanceof Byte) {
      return ((Number) o).longValue();
    }
    if (o instanceof Float) {
      return ((Float) o).doubleValue();
    }
    return o;
  }

  // ---------------------------------------------------------------- to_s / inspect

  /** Ruby {@code to_s} as used for Liquid output ({@code BlockBody#render_node_to_output}). */
  public static String toS(Object o) {
    if (o == null) {
      return "";
    }
    if (o instanceof String) {
      return (String) o;
    }
    if (o instanceof Long || o instanceof Integer || o instanceof BigInteger) {
      return o.toString();
    }
    if (o instanceof Double) {
      return floatToS((Double) o);
    }
    if (o instanceof Float) {
      return floatToS(((Float) o).doubleValue());
    }
    if (o instanceof Boolean) {
      return o.toString();
    }
    if (o instanceof List) {
      StringBuilder sb = new StringBuilder();
      join((List<?>) o, "", sb, new IdentityHashMap<>());
      return sb.toString();
    }
    if (o instanceof Map) {
      return inspect(o);
    }
    if (o instanceof Drop) {
      return ((Drop) o).toS();
    }
    if (o instanceof Expression.MethodLiteral) {
      return "";
    }
    if (o instanceof BigDecimal) {
      return floatToS(((BigDecimal) o).doubleValue());
    }
    return o.toString();
  }

  /**
   * Ruby's real {@code to_s} (what filters see via {@code input.to_s}): like {@link #toS} except an
   * Array becomes its {@code inspect} form ({@code [1, "a"]}) instead of being joined.
   */
  public static String rubyToS(Object o) {
    return o instanceof List ? inspect(o) : toS(o);
  }

  /** Ruby {@code Array#join(sep)} (recursive on nested arrays). */
  public static String join(List<?> list, String sep) {
    StringBuilder sb = new StringBuilder();
    join(list, sep, sb, new IdentityHashMap<>());
    return sb.toString();
  }

  private static void join(List<?> list, String sep, StringBuilder sb, IdentityHashMap<Object, Boolean> seen) {
    if (seen.put(list, Boolean.TRUE) != null) {
      throw RubyError.argumentError("recursive array join");
    }
    boolean first = true;
    for (Object e : list) {
      if (!first) {
        sb.append(sep);
      }
      first = false;
      if (e instanceof List) {
        join((List<?>) e, sep, sb, seen);
      } else {
        sb.append(toS(e));
      }
    }
    seen.remove(list);
  }

  /** Ruby {@code inspect}. */
  public static String inspect(Object o) {
    StringBuilder sb = new StringBuilder();
    inspect(o, sb);
    return sb.toString();
  }

  private static void inspect(Object o, StringBuilder sb) {
    if (o == null) {
      sb.append("nil");
    } else if (o instanceof String) {
      inspectString((String) o, sb);
    } else if (o instanceof List) {
      sb.append('[');
      boolean first = true;
      for (Object e : (List<?>) o) {
        if (!first) {
          sb.append(", ");
        }
        first = false;
        inspect(e, sb);
      }
      sb.append(']');
    } else if (o instanceof Map) {
      Map<?, ?> m = (Map<?, ?>) o;
      if (m.isEmpty()) {
        sb.append("{}");
        return;
      }
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (!first) {
          sb.append(", ");
        }
        first = false;
        inspect(e.getKey(), sb);
        sb.append("=>");
        inspect(e.getValue(), sb);
      }
      sb.append('}');
    } else if (o instanceof Drop) {
      sb.append(((Drop) o).inspect());
    } else if (o instanceof RubyTime) {
      sb.append(((RubyTime) o).inspect());
    } else if (o instanceof Expression.MethodLiteral) {
      sb.append("#<Liquid::Expression::MethodLiteral>");
    } else {
      sb.append(toS(o));
    }
  }

  static void inspectString(String s, StringBuilder sb) {
    sb.append('"');
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n"); break;
        case '\t': sb.append("\\t"); break;
        case '\r': sb.append("\\r"); break;
        case '\f': sb.append("\\f"); break;
        case 0x0b: sb.append("\\v"); break;
        case 0x07: sb.append("\\a"); break;
        case 0x08: sb.append("\\b"); break;
        case 0x1b: sb.append("\\e"); break;
        case '#':
          if (i + 1 < n && (s.charAt(i + 1) == '{' || s.charAt(i + 1) == '$' || s.charAt(i + 1) == '@')) {
            sb.append("\\#");
          } else {
            sb.append('#');
          }
          break;
        default:
          if (c < 0x20 || c == 0x7f) {
            sb.append(String.format(java.util.Locale.ROOT, "\\u%04X", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
  }

  /** Ruby {@code Float#to_s}: shortest round-trip digits, fixed notation for 1e-4 <= |x| < 1e16. */
  public static String floatToS(double d) {
    if (Double.isNaN(d)) {
      return "NaN";
    }
    if (Double.isInfinite(d)) {
      return d > 0 ? "Infinity" : "-Infinity";
    }
    if (d == 0.0) {
      return (1.0 / d < 0) ? "-0.0" : "0.0";
    }
    // JDK 19+ Double.toString produces the shortest uniquely-identifying digits, like Ruby.
    BigDecimal bd = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
    if (bd.precision() == 2) {
      // Java's toString never emits fewer than 2 significant digits (4.9E-324); Ruby's shortest may be 1 (5.0e-324)
      BigDecimal one = bd.round(new java.math.MathContext(1, java.math.RoundingMode.HALF_EVEN));
      if (one.doubleValue() == Math.abs(d)) {
        bd = one.stripTrailingZeros();
      }
    }
    String digits = bd.unscaledValue().toString();
    int decpt = digits.length() - bd.scale();
    StringBuilder sb = new StringBuilder();
    if (d < 0) {
      sb.append('-');
    }
    if (decpt > 0 && decpt <= 16 - 1) {
      if (digits.length() <= decpt) {
        sb.append(digits);
        for (int k = digits.length(); k < decpt; k++) {
          sb.append('0');
        }
        sb.append(".0");
      } else {
        sb.append(digits, 0, decpt).append('.').append(digits, decpt, digits.length());
      }
    } else if (decpt <= 0 && decpt > -4) {
      sb.append("0.");
      for (int k = 0; k < -decpt; k++) {
        sb.append('0');
      }
      sb.append(digits);
    } else {
      sb.append(digits.charAt(0)).append('.');
      sb.append(digits.length() > 1 ? digits.substring(1) : "0");
      int e = decpt - 1;
      sb.append('e').append(e < 0 ? '-' : '+');
      int ae = Math.abs(e);
      if (ae < 10) {
        sb.append('0');
      }
      sb.append(ae);
    }
    return sb.toString();
  }

  // ---------------------------------------------------------------- equality

  /** Ruby {@code a == b}. */
  public static boolean rubyEquals(Object a, Object b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (isNumeric(a) && isNumeric(b)) {
      return numCompare(a, b) == 0;
    }
    if (a instanceof String || b instanceof String) {
      return a.equals(b);
    }
    if (a instanceof Boolean || b instanceof Boolean) {
      return a.equals(b);
    }
    if (a instanceof List && b instanceof List) {
      List<?> la = (List<?>) a, lb = (List<?>) b;
      if (la.size() != lb.size()) {
        return false;
      }
      Iterator<?> ia = la.iterator(), ib = lb.iterator();
      while (ia.hasNext()) {
        if (!rubyEquals(ia.next(), ib.next())) {
          return false;
        }
      }
      return true;
    }
    if (a instanceof Map && b instanceof Map) {
      Map<?, ?> ma = (Map<?, ?>) a, mb = (Map<?, ?>) b;
      if (ma.size() != mb.size()) {
        return false;
      }
      for (Map.Entry<?, ?> e : ma.entrySet()) {
        if (!mb.containsKey(e.getKey()) || !rubyEquals(e.getValue(), mb.get(e.getKey()))) {
          return false;
        }
      }
      return true;
    }
    return a.equals(b);
  }

  /** Ruby {@code eql?}: like == but 1 and 1.0 differ (used by uniq / hash keys). */
  public static boolean rubyEql(Object a, Object b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (isInteger(a) && isInteger(b)) {
      return toBigInteger(a).equals(toBigInteger(b));
    }
    if (isFloat(a) && isFloat(b)) {
      return ((Number) a).doubleValue() == ((Number) b).doubleValue();
    }
    if (isNumeric(a) || isNumeric(b)) {
      return false;
    }
    if (a instanceof List && b instanceof List) {
      List<?> la = (List<?>) a, lb = (List<?>) b;
      if (la.size() != lb.size()) {
        return false;
      }
      for (int i = 0; i < la.size(); i++) {
        if (!rubyEql(la.get(i), lb.get(i))) {
          return false;
        }
      }
      return true;
    }
    if (a instanceof Map && b instanceof Map) {
      Map<?, ?> ma = (Map<?, ?>) a, mb = (Map<?, ?>) b;
      if (ma.size() != mb.size()) {
        return false;
      }
      for (Map.Entry<?, ?> e : ma.entrySet()) {
        if (!mb.containsKey(e.getKey()) || !rubyEql(e.getValue(), mb.get(e.getKey()))) {
          return false;
        }
      }
      return true;
    }
    return a.equals(b);
  }

  public static int rubyHash(Object a) {
    if (a == null) {
      return 0;
    }
    if (isInteger(a)) {
      return toBigInteger(a).hashCode();
    }
    if (isFloat(a)) {
      return Double.hashCode(((Number) a).doubleValue()) * 31;
    }
    if (a instanceof List) {
      int h = 1;
      for (Object e : (List<?>) a) {
        h = h * 31 + rubyHash(e);
      }
      return h;
    }
    if (a instanceof Map) {
      int h = 7;
      for (Map.Entry<?, ?> e : ((Map<?, ?>) a).entrySet()) {
        h += rubyHash(e.getKey()) ^ rubyHash(e.getValue());
      }
      return h;
    }
    return a.hashCode();
  }

  /** Wrapper giving a value Ruby eql?/hash semantics, for use as a Java map key. */
  public static final class EqlKey {
    final Object v;

    public EqlKey(Object v) {
      this.v = v;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof EqlKey && rubyEql(v, ((EqlKey) o).v);
    }

    @Override
    public int hashCode() {
      return rubyHash(v);
    }
  }

  // ---------------------------------------------------------------- comparison

  static int numCompare(Object a, Object b) {
    if (isInteger(a) && isInteger(b)) {
      if (a instanceof Long && b instanceof Long) {
        return Long.compare((Long) a, (Long) b);
      }
      return toBigInteger(a).compareTo(toBigInteger(b));
    }
    if (a instanceof BigDecimal || b instanceof BigDecimal) {
      return toBigDecimal(a).compareTo(toBigDecimal(b));
    }
    double da = ((Number) a).doubleValue(), db = ((Number) b).doubleValue();
    if (isInteger(a) && a instanceof BigInteger || isInteger(b) && b instanceof BigInteger) {
      return toBigDecimal(a).compareTo(toBigDecimal(b));
    }
    return Double.compare(da == 0.0 ? 0.0 : da, db == 0.0 ? 0.0 : db);
  }

  static BigDecimal toBigDecimal(Object o) {
    if (o instanceof BigDecimal) {
      return (BigDecimal) o;
    }
    if (o instanceof BigInteger) {
      return new BigDecimal((BigInteger) o);
    }
    if (isInteger(o)) {
      return BigDecimal.valueOf(((Number) o).longValue());
    }
    return new BigDecimal(Double.toString(((Number) o).doubleValue()));
  }

  /** Ruby codepoint (== UTF-8 byte) order string comparison. */
  public static int compareStrings(String a, String b) {
    int n = Math.min(a.length(), b.length());
    for (int i = 0; i < n; i++) {
      char ca = a.charAt(i), cb = b.charAt(i);
      if (ca != cb) {
        boolean sa = Character.isSurrogate(ca), sb = Character.isSurrogate(cb);
        if (sa != sb) {
          return sa ? 1 : -1; // surrogates encode code points above every BMP char
        }
        return ca < cb ? -1 : 1;
      }
    }
    return Integer.compare(a.length(), b.length());
  }

  /** Ruby {@code a <=> b}; null when not comparable. */
  public static Integer spaceship(Object a, Object b) {
    if (isNumeric(a) && isNumeric(b)) {
      if (isFloat(a) && Double.isNaN(((Number) a).doubleValue()) || isFloat(b) && Double.isNaN(((Number) b).doubleValue())) {
        return null;
      }
      return Integer.signum(numCompare(a, b));
    }
    if (a instanceof String && b instanceof String) {
      return Integer.signum(compareStrings((String) a, (String) b));
    }
    if (a instanceof List && b instanceof List) {
      List<?> la = (List<?>) a, lb = (List<?>) b;
      int n = Math.min(la.size(), lb.size());
      for (int i = 0; i < n; i++) {
        Integer c = spaceship(la.get(i), lb.get(i));
        if (c == null || c != 0) {
          return c;
        }
      }
      return Integer.compare(la.size(), lb.size());
    }
    if (a instanceof RubyTime && b instanceof RubyTime) {
      return Integer.signum(((RubyTime) a).compareTo((RubyTime) b));
    }
    // Object#<=>: 0 when ==, else nil
    return rubyEquals(a, b) ? 0 : null;
  }

  /** Ruby rb_cmperr: "comparison of X with Y failed" as an ::ArgumentError. */
  public static RubyError cmpErr(Object a, Object b) {
    String bn = (b == null || b instanceof Boolean || isInteger(b) || isFloat(b)) ? inspect(b) : className(b);
    return RubyError.argumentError("comparison of " + className(a) + " with " + bn + " failed");
  }

  /** Ruby's {@code <}, {@code >}, {@code <=}, {@code >=} on two values that both respond to them. */
  public static boolean relop(String op, Object a, Object b) {
    Integer c;
    if (isNumeric(a)) {
      if (!isNumeric(b)) {
        throw cmpErr(a, b);
      }
      if (isFloat(a) && Double.isNaN(((Number) a).doubleValue()) || isFloat(b) && Double.isNaN(((Number) b).doubleValue())) {
        return false;
      }
      c = numCompare(a, b);
    } else {
      c = spaceship(a, b);
      if (c == null) {
        throw cmpErr(a, b);
      }
    }
    switch (op) {
      case "<": return c < 0;
      case ">": return c > 0;
      case "<=": return c <= 0;
      case ">=": return c >= 0;
      default: throw new IllegalArgumentException(op);
    }
  }

  /** Whether a value responds to {@code <} etc. (Comparable / Numeric). */
  public static boolean respondsToRelop(Object o) {
    return isNumeric(o) || o instanceof String || o instanceof RubyTime;
  }

  // ---------------------------------------------------------------- conversions

  /** Ruby {@code to_i}. */
  public static Object toI(Object o) {
    if (o == null) {
      return 0L;
    }
    if (isInteger(o)) {
      return normInt(o);
    }
    if (isFloat(o) || o instanceof BigDecimal) {
      double d = ((Number) o).doubleValue();
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        throw new RubyError("FloatDomainError", floatToS(d));
      }
      if (Math.abs(d) < 9.0e18) {
        return (long) d;
      }
      return normInt(new BigDecimal(d).toBigInteger());
    }
    if (o instanceof String) {
      return RubyStrings.toI((String) o);
    }
    if (o instanceof RubyTime) {
      return ((RubyTime) o).epochSecond();
    }
    throw RubyError.noMethod("to_i", o);
  }

  /** Ruby to_i when the caller needs a Java long (clamped for bignums). */
  public static long toLong(Object o) {
    Object i = toI(o);
    if (i instanceof Long) {
      return (Long) i;
    }
    return ((BigInteger) i).signum() > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
  }

  // ---------------------------------------------------------------- obj[key]

  /** Whether {@code obj.respond_to?(:[])}. */
  public static boolean respondsToIndex(Object o) {
    return o instanceof Map || o instanceof List || o instanceof String || o instanceof Drop || isInteger(o);
  }

  /** Ruby {@code obj[key]} for the value model. */
  public static Object index(Object obj, Object key) {
    if (obj instanceof Map) {
      return ((Map<?, ?>) obj).get(key);
    }
    if (obj instanceof Drop) {
      return ((Drop) obj).invokeDrop(key);
    }
    if (obj instanceof List) {
      List<?> l = (List<?>) obj;
      long i = indexArg(key);
      if (i < 0) {
        i += l.size();
      }
      return i >= 0 && i < l.size() ? l.get((int) i) : null;
    }
    if (obj instanceof String) {
      String s = (String) obj;
      if (key instanceof String) {
        return s.contains((String) key) ? key : null;
      }
      long i = indexArg(key);
      return RubyStrings.slice(s, i, 1);
    }
    if (isInteger(obj)) {
      long i = indexArg(key);
      if (i < 0) {
        return 0L;
      }
      return toBigInteger(obj).testBit((int) Math.min(i, Integer.MAX_VALUE)) ? 1L : 0L;
    }
    throw RubyError.noMethod("[]", obj);
  }

  private static long indexArg(Object key) {
    if (isInteger(key)) {
      return toLong(key);
    }
    if (isFloat(key)) {
      return (long) ((Number) key).doubleValue();
    }
    if (key == null) {
      throw RubyError.typeError("no implicit conversion from nil to integer");
    }
    throw RubyError.typeError("no implicit conversion of " + className(key) + " into Integer");
  }

  /** Ruby {@code obj.respond_to?(:size)} etc. for the command methods size/first/last. */
  public static boolean respondsTo(Object o, String method) {
    switch (method) {
      case "size":
        return o instanceof String || o instanceof List || o instanceof Map || o instanceof RubyRange || isInteger(o);
      case "first":
        return o instanceof List || o instanceof Map || o instanceof RubyRange;
      case "last":
        return o instanceof List || o instanceof RubyRange;
      case "empty?":
        return o instanceof String || o instanceof List || o instanceof Map;
      default:
        return false;
    }
  }

  /** Ruby {@code o.send(method)} for size/first/last/empty?. */
  public static Object send(Object o, String method) {
    switch (method) {
      case "size":
        if (o instanceof String) {
          return (long) RubyStrings.length((String) o);
        }
        if (o instanceof Collection) {
          return (long) ((Collection<?>) o).size();
        }
        if (o instanceof Map) {
          return (long) ((Map<?, ?>) o).size();
        }
        if (o instanceof RubyRange) {
          return ((RubyRange) o).size();
        }
        if (o instanceof Long) {
          return 8L;
        }
        if (isInteger(o)) {
          return (long) ((toBigInteger(o).bitLength() + 64) / 64 * 8);
        }
        break;
      case "first":
        if (o instanceof List) {
          List<?> l = (List<?>) o;
          return l.isEmpty() ? null : l.get(0);
        }
        if (o instanceof Map) {
          Map<?, ?> m = (Map<?, ?>) o;
          if (m.isEmpty()) {
            return null;
          }
          Map.Entry<?, ?> e = m.entrySet().iterator().next();
          List<Object> pair = new ArrayList<>(2);
          pair.add(e.getKey());
          pair.add(e.getValue());
          return pair;
        }
        if (o instanceof RubyRange) {
          return ((RubyRange) o).first;
        }
        break;
      case "last":
        if (o instanceof List) {
          List<?> l = (List<?>) o;
          return l.isEmpty() ? null : l.get(l.size() - 1);
        }
        if (o instanceof RubyRange) {
          return ((RubyRange) o).last;
        }
        break;
      case "empty?":
        if (o instanceof String) {
          return ((String) o).isEmpty();
        }
        if (o instanceof Collection) {
          return ((Collection<?>) o).isEmpty();
        }
        if (o instanceof Map) {
          return ((Map<?, ?>) o).isEmpty();
        }
        break;
      default:
        break;
    }
    throw RubyError.noMethod(method, o);
  }

  /** Ruby {@code Array#flatten}. */
  public static List<Object> flatten(List<?> l) {
    List<Object> out = new ArrayList<>(l.size());
    flatten(l, out, new IdentityHashMap<>());
    return out;
  }

  private static void flatten(List<?> l, List<Object> out, IdentityHashMap<Object, Boolean> seen) {
    if (seen.put(l, Boolean.TRUE) != null) {
      throw RubyError.argumentError("tried to flatten recursive array");
    }
    for (Object e : l) {
      if (e instanceof List) {
        flatten((List<?>) e, out, seen);
      } else {
        out.add(e);
      }
    }
    seen.remove(l);
  }

  static boolean containsEql(Set<EqlKey> set, Object v) {
    return set.contains(new EqlKey(v));
  }
}
