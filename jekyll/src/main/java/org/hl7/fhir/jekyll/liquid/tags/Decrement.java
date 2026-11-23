package org.hl7.fhir.jekyll.liquid.tags;

import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.RubyError;
import org.hl7.fhir.jekyll.liquid.RubyStrings;
import org.hl7.fhir.jekyll.liquid.Tag;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/decrement.rb. */
public class Decrement extends Tag {
  private final String variable;

  public Decrement(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    variable = RubyStrings.strip(markup);
  }

  @Override
  public Object render(Context context) {
    Object env = context.environments().get(0);
    Object value = Increment.Counters.get(env, variable);
    if (!Values.truthy(value)) {
      value = 0L;
      Increment.Counters.put(env, variable, value);
    }
    if (value instanceof String) {
      throw RubyError.noMethod("-", value);
    }
    value = Increment.Counters.plus(value, -1);
    Increment.Counters.put(env, variable, value);
    return Values.toS(value);
  }
}
