package org.hl7.fhir.jekyll.liquid.tags;

import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/ifchanged.rb. */
public class Ifchanged extends Block {
  public Ifchanged(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
  }

  @Override
  public Object render(Context context) {
    return context.stack(() -> {
      String output = Values.toS(super.render(context));
      if (!output.equals(context.registers().get("ifchanged"))) {
        context.registers().put("ifchanged", output);
        return output;
      }
      return "";
    });
  }
}
