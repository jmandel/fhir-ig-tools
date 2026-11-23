package org.hl7.fhir.jekyll.liquid.tags;

import org.hl7.fhir.jekyll.liquid.Condition;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/unless.rb. */
public class Unless extends If {
  public Unless(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
  }

  @Override
  public Object render(Context context) {
    return context.stack(() -> {
      // First condition is interpreted backwards ( if not )
      Condition firstBlock = blocks.get(0);
      if (!Values.truthy(firstBlock.evaluate(context))) {
        return firstBlock.attachment().render(context);
      }
      // After the first condition unless works just like if
      for (int i = 1; i < blocks.size(); i++) {
        Condition block = blocks.get(i);
        if (Values.truthy(block.evaluate(context))) {
          return block.attachment().render(context);
        }
      }
      return "";
    });
  }
}
