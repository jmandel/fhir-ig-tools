package org.hl7.fhir.jekyll.liquid;

import java.util.List;
import java.util.Map;

/** Port of liquid-4.0.4/lib/liquid/strainer.rb: dispatches filter calls for a Context. */
final class Strainer {
  private Strainer() {}

  static Object invoke(Context context, String method, Object input, List<Object> args) {
    Filter f = null;
    Map<String, Filter> local = context.localFilters();
    if (local != null) {
      f = local.get(method);
    }
    if (f == null) {
      f = context.environment().filter(method);
    }
    if (f != null) {
      try {
        return f.apply(context, input, args);
      } catch (RubyError e) {
        if (e.isArgumentError()) {
          throw new LiquidException(LiquidException.Kind.ARGUMENT, e.getMessage(), e);
        }
        throw e;
      }
    } else if (context.strictFilters()) {
      throw new LiquidException(LiquidException.Kind.UNDEFINED_FILTER, "undefined filter " + method);
    } else {
      return input;
    }
  }
}
