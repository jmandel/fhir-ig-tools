package org.hl7.fhir.jekyll.test;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.liquid.RubyTime;
import org.hl7.fhir.jekyll.yaml.BinaryString;
import org.hl7.fhir.jekyll.yaml.RubyDate;
import org.hl7.fhir.jekyll.yaml.RubySymbol;
import org.hl7.fhir.jekyll.yaml.RubyValues;
import org.hl7.fhir.jekyll.yaml.Yaml;
import org.hl7.fhir.jekyll.yaml.YamlException;

/**
 * Registers the YAML port with DiffRunner (run through {@code org.hl7.fhir.jekyll.test.yaml.YamlDiff}):
 *
 * <ul>
 *   <li>"yaml" / "data-file": the existing site case types (JSON.generate of SafeYAML.load /
 *       load_file), now backed by {@link Yaml#load} / {@link Yaml#loadFile};
 *   <li>"yaml-typed" / "yaml-typed-file": oracle.rb's typed encoding, errors as "ERR Class: message"
 *       (so Psych's error messages are compared too).
 * </ul>
 */
public final class YamlCaseRunner {
  static {
    DiffRunner.IMPLS.put("yaml", c -> run(() -> rubyJson(Yaml.load((String) c.get("text")))));
    DiffRunner.IMPLS.put("data-file", c -> run(() -> rubyJson(Yaml.loadFile(Path.of((String) c.get("host_path"))))));
    DiffRunner.IMPLS.put("yaml-typed", c -> Map.of("output", typedOrError(() -> Yaml.load((String) c.get("text")))));
    DiffRunner.IMPLS.put("yaml-typed-file", c -> Map.of("output", typedOrError(() -> Yaml.loadFile(Path.of((String) c.get("path"))))));
  }

  private YamlCaseRunner() {}

  interface Load {
    Object get();
  }

  private static Map<String, Object> run(java.util.function.Supplier<String> f) {
    try {
      return Map.of("output", f.get());
    } catch (RuntimeException e) {
      return Map.of("error", e.toString());
    }
  }

  public static String typedOrError(Load f) {
    Object v;
    try {
      v = f.get();
    } catch (YamlException e) {
      return "ERR " + e;
    }
    StringBuilder b = new StringBuilder();
    writeJson(typed(v, new IdentityHashMap<>()), b);
    return b.toString();
  }

  /** oracle.rb yaml_typed */
  static Object typed(Object v, IdentityHashMap<Object, Boolean> path) {
    if (v == null) {
      return "n";
    }
    if (v instanceof Boolean) {
      return ((Boolean) v) ? "T" : "F";
    }
    if (v instanceof String) {
      return List.of("s", v);
    }
    if (v instanceof BinaryString) {
      StringBuilder hex = new StringBuilder();
      for (byte x : ((BinaryString) v).bytes()) {
        hex.append(String.format("%02x", x & 0xFF));
      }
      return List.of("b", hex.toString());
    }
    if (v instanceof Long || v instanceof BigInteger) {
      return List.of("i", v.toString());
    }
    if (v instanceof Double) {
      return List.of("f", RubyValues.toS(v));
    }
    if (v instanceof RubySymbol) {
      return List.of("y", v.toString());
    }
    if (v instanceof RubyDate) {
      return List.of("d", ((RubyDate) v).toS(), new Raw(Long.toString(((RubyDate) v).jd())));
    }
    if (v instanceof RubyTime) {
      RubyTime t = (RubyTime) v;
      return List.of("t", timeString(t), new Raw(t.isUtc() ? "true" : "false"));
    }
    if (v instanceof List || v instanceof Map) {
      if (path.containsKey(v)) {
        return List.of("rec");
      }
      path.put(v, true);
      java.util.ArrayList<Object> r = new java.util.ArrayList<>();
      if (v instanceof List) {
        r.add("a");
        for (Object x : (List<?>) v) {
          r.add(typed(x, path));
        }
      } else {
        r.add("h");
        for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
          r.add(List.of(typed(e.getKey(), path), typed(e.getValue(), path)));
        }
      }
      path.remove(v);
      return r;
    }
    return List.of("?", v.getClass().getName(), v.toString());
  }

  /** Ruby t.strftime("%Y-%m-%d %H:%M:%S.%N %z"), computed from the value's fields (not the Liquid port's strftime). */
  static String timeString(RubyTime t) {
    java.time.LocalDateTime w = t.wall();
    int y = w.getYear();
    String ys = y < 0 ? "-" + String.format("%04d", -(long) y) : String.format("%04d", y);
    int off = t.offset().getTotalSeconds();
    int a = Math.abs(off);
    return ys + String.format("-%02d-%02d %02d:%02d:%02d.%09d %s%02d%02d", w.getMonthValue(), w.getDayOfMonth(), w.getHour(),
        w.getMinute(), w.getSecond(), t.nano(), off < 0 ? "-" : "+", a / 3600, (a % 3600) / 60);
  }

  /** a pre-rendered JSON literal */
  static final class Raw {
    final String json;

    Raw(String json) {
      this.json = json;
    }
  }

  private static void writeJson(Object v, StringBuilder b) {
    if (v instanceof Raw) {
      b.append(((Raw) v).json);
    } else if (v instanceof String) {
      quote((String) v, b);
    } else {
      b.append('[');
      boolean first = true;
      for (Object o : (List<?>) v) {
        if (!first) {
          b.append(',');
        }
        first = false;
        writeJson(o, b);
      }
      b.append(']');
    }
  }

  /** Ruby JSON.generate (json 2.6.3) of a loaded value: Float#to_s, to_s for other objects and keys. */
  static String rubyJson(Object v) {
    StringBuilder b = new StringBuilder();
    gen(v, b, 0);
    return b.toString();
  }

  private static void gen(Object v, StringBuilder b, int depth) {
    if (v == null) {
      b.append("null");
    } else if (v instanceof String) {
      quote((String) v, b);
    } else if (v instanceof BinaryString) {
      throw new IllegalStateException("JSON::GeneratorError: source sequence is illegal/malformed utf-8");
    } else if (v instanceof Double) {
      double d = (Double) v;
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        throw new IllegalStateException("JSON::GeneratorError: " + RubyValues.toS(v) + " not allowed in JSON");
      }
      b.append(RubyValues.toS(v));
    } else if (v instanceof Boolean || v instanceof Long || v instanceof BigInteger) {
      b.append(v);
    } else if (v instanceof Map) {
      if (++depth > 100) {
        throw new IllegalStateException("JSON::NestingError: nesting of " + depth + " is too deep");
      }
      b.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
        if (!first) {
          b.append(',');
        }
        first = false;
        quote(RubyValues.toS(e.getKey()), b);
        b.append(':');
        gen(e.getValue(), b, depth);
      }
      b.append('}');
    } else if (v instanceof List) {
      if (++depth > 100) {
        throw new IllegalStateException("JSON::NestingError: nesting of " + depth + " is too deep");
      }
      b.append('[');
      boolean first = true;
      for (Object o : (List<?>) v) {
        if (!first) {
          b.append(',');
        }
        first = false;
        gen(o, b, depth);
      }
      b.append(']');
    } else {
      quote(RubyValues.toS(v), b);
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
