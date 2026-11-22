package org.hl7.fhir.jekyll.yaml;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.liquid.RubyTime;

/**
 * Small Ruby-semantics helpers for loaded values: class names (for TypeError messages),
 * {@code inspect}/{@code to_s} of the value model (what {@code JSON.generate} uses for non-string
 * hash keys), and {@code String#split(String)}.
 */
public final class RubyValues {
  private RubyValues() {}

  /** Ruby class name of a loaded value. */
  public static String className(Object v) {
    if (v == null) {
      return "NilClass";
    }
    if (v instanceof String || v instanceof BinaryString) {
      return "String";
    }
    if (v instanceof Long || v instanceof BigInteger || v instanceof Integer) {
      return "Integer";
    }
    if (v instanceof Double) {
      return "Float";
    }
    if (v instanceof Boolean) {
      return ((Boolean) v) ? "TrueClass" : "FalseClass";
    }
    if (v instanceof List) {
      return "Array";
    }
    if (v instanceof Map) {
      return "Hash";
    }
    if (v instanceof RubyDate) {
      return "Date";
    }
    if (v instanceof RubyTime) {
      return "Time";
    }
    if (v instanceof RubySymbol) {
      return "Symbol";
    }
    return v.getClass().getSimpleName();
  }

  /** String#split(sep) for a literal, non-" " separator (trailing empty fields removed). */
  static String[] split(String s, String sep) {
    List<String> out = new ArrayList<>();
    int start = 0;
    int k;
    while ((k = s.indexOf(sep, start)) >= 0) {
      out.add(s.substring(start, k));
      start = k + sep.length();
    }
    out.add(s.substring(start));
    int n = out.size();
    while (n > 0 && out.get(n - 1).isEmpty()) {
      n--;
    }
    return out.subList(0, n).toArray(new String[0]);
  }

  /** Ruby {@code to_s} of a loaded value (as {@code JSON.generate} applies it to hash keys). */
  public static String toS(Object v) {
    if (v == null) {
      return "";
    }
    if (v instanceof String) {
      return (String) v;
    }
    if (v instanceof Double) {
      return RubyNumbers.floatToS((Double) v);
    }
    if (v instanceof RubyDate) {
      return ((RubyDate) v).toS();
    }
    if (v instanceof RubyTime) {
      return ((RubyTime) v).toS();
    }
    if (v instanceof List || v instanceof Map) {
      return inspect(v);
    }
    return v.toString();
  }

  /** Ruby {@code inspect} of a loaded value. */
  public static String inspect(Object v) {
    StringBuilder sb = new StringBuilder();
    inspect(v, sb, new IdentityHashMap<>());
    return sb.toString();
  }

  private static void inspect(Object v, StringBuilder sb, IdentityHashMap<Object, Boolean> seen) {
    if (v == null) {
      sb.append("nil");
    } else if (v instanceof String) {
      inspectString((String) v, sb);
    } else if (v instanceof Double) {
      sb.append(RubyNumbers.floatToS((Double) v));
    } else if (v instanceof RubyDate) {
      sb.append(((RubyDate) v).inspect());
    } else if (v instanceof RubyTime) {
      sb.append(((RubyTime) v).inspect());
    } else if (v instanceof RubySymbol) {
      sb.append(':').append(((RubySymbol) v).name());
    } else if (v instanceof List) {
      if (seen.containsKey(v)) {
        sb.append("[...]");
        return;
      }
      seen.put(v, true);
      sb.append('[');
      boolean first = true;
      for (Object o : (List<?>) v) {
        if (!first) {
          sb.append(", ");
        }
        first = false;
        inspect(o, sb, seen);
      }
      sb.append(']');
      seen.remove(v);
    } else if (v instanceof Map) {
      if (seen.containsKey(v)) {
        sb.append("{...}");
        return;
      }
      seen.put(v, true);
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
        if (!first) {
          sb.append(", ");
        }
        first = false;
        inspect(e.getKey(), sb, seen);
        sb.append("=>");
        inspect(e.getValue(), sb, seen);
      }
      sb.append('}');
      seen.remove(v);
    } else {
      sb.append(v);
    }
  }

  /** String#inspect for a valid UTF-8 string. */
  static String inspect(String s) {
    StringBuilder sb = new StringBuilder();
    inspectString(s, sb);
    return sb.toString();
  }

  private static void inspectString(String s, StringBuilder sb) {
    sb.append('"');
    for (int i = 0; i < s.length(); ) {
      int c = s.codePointAt(i);
      int w = Character.charCount(c);
      switch (c) {
        case '"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        case '\f': sb.append("\\f"); break;
        case 0x0B: sb.append("\\v"); break;
        case 0x08: sb.append("\\b"); break;
        case 0x07: sb.append("\\a"); break;
        case 0x1B: sb.append("\\e"); break;
        case '#': {
          int n = i + 1 < s.length() ? s.charAt(i + 1) : 0;
          sb.append(n == '{' || n == '$' || n == '@' ? "\\#" : "#");
          break;
        }
        default:
          if (c < 0x20 || c == 0x7F || !isPrintable(c)) {
            sb.append(c > 0xFFFF ? String.format(java.util.Locale.ROOT, "\\u{%X}", c) : String.format(java.util.Locale.ROOT, "\\u%04X", c));
          } else {
            sb.appendCodePoint(c);
          }
      }
      i += w;
    }
    sb.append('"');
  }

  /** Onigmo's printable test for UTF-8 (graph or space), approximated with Java's categories. */
  private static boolean isPrintable(int c) {
    switch (Character.getType(c)) {
      case Character.CONTROL:
      case Character.FORMAT:
      case Character.UNASSIGNED:
      case Character.SURROGATE:
      case Character.PRIVATE_USE:
      case Character.LINE_SEPARATOR:
      case Character.PARAGRAPH_SEPARATOR:
        return false;
      case Character.SPACE_SEPARATOR:
        return true;
      default:
        return true;
    }
  }
}
