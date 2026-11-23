package org.hl7.fhir.jekyll.test;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.site.DataFiles;

/** Registers site-module case types with DiffRunner: "yaml" (Jekyll's SafeYAML load of config/data). */
public final class SiteCaseRunner {
  static {
    DiffRunner.IMPLS.put("jekyll-liquid", JekyllLiquidCaseRunner::run);
  }

  private SiteCaseRunner() {}

  /** Ruby JSON.generate formatting (compact; Float#to_s for floats; minimal string escaping). */
  @SuppressWarnings("unchecked")
  static String rubyJson(Object v) {
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
    } else if (v instanceof Double) {
      b.append(rubyFloat((Double) v));
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
      b.append(v);
    }
  }

  static String rubyFloat(double d) {
    if (d == Math.rint(d) && Math.abs(d) < 1e16) {
      return String.valueOf((long) d) + ".0";
    }
    String s = Double.toString(d);
    if (s.contains("E")) {
      String[] p = s.split("E");
      int exp = Integer.parseInt(p[1]);
      return p[0] + "e" + (exp < 0 ? "-" : "+") + String.format("%02d", Math.abs(exp));
    }
    return s;
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
        case '\b': b.append("\\b"); break;
        case '\f': b.append("\\f"); break;
        default:
          if (c < 0x20) {
            b.append(String.format("\\u%04x", (int) c));
          } else {
            b.append(c);
          }
      }
    }
    b.append('"');
  }
}
