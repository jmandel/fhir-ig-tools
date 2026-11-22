package org.hl7.fhir.jekyll.liquid.tags;

import java.util.Map;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Drop;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.RubyError;
import org.hl7.fhir.jekyll.liquid.RubyStrings;
import org.hl7.fhir.jekyll.liquid.Tag;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/increment.rb. */
public class Increment extends Tag {
  private final String variable;

  public Increment(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    variable = RubyStrings.strip(markup);
  }

  @Override
  public Object render(Context context) {
    Object env = context.environments().get(0);
    Object value = Counters.get(env, variable);
    if (!Values.truthy(value)) {
      value = 0L;
      Counters.put(env, variable, value);
    }
    Counters.put(env, variable, Counters.plus(value, 1));
    return Values.toS(value);
  }

  /** Helpers shared by increment/decrement ({@code environments.first[@variable]}). */
  static final class Counters {
    private Counters() {}

    @SuppressWarnings("unchecked")
    static Object get(Object env, String key) {
      if (env instanceof Map) {
        return ((Map<String, Object>) env).get(key);
      }
      if (env instanceof Drop) {
        return ((Drop) env).invokeDrop(key);
      }
      throw RubyError.noMethod("[]", env);
    }

    @SuppressWarnings("unchecked")
    static void put(Object env, String key, Object v) {
      if (env instanceof Map) {
        ((Map<String, Object>) env).put(key, v);
      } else if (env instanceof Drop) {
        ((Drop) env).put(key, v);
      } else {
        throw RubyError.noMethod("[]=", env);
      }
    }

    static Object plus(Object value, long d) {
      if (value instanceof Long) {
        return (Long) value + d;
      }
      if (Values.isFloat(value)) {
        return ((Number) value).doubleValue() + d;
      }
      if (value instanceof String) {
        throw RubyError.typeError("no implicit conversion of Integer into String");
      }
      throw RubyError.noMethod(d > 0 ? "+" : "-", value);
    }
  }
}
