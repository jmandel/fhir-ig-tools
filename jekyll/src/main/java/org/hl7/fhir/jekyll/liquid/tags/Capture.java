package org.hl7.fhir.jekyll.liquid.tags;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/capture.rb. */
public class Capture extends Block {
  static final Pattern Syntax = Pattern.compile("(" + "(?:" + Liquid.VARIABLE_SIGNATURE + ")" + "+)", Pattern.UNIX_LINES);

  private final String to;

  public Capture(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    Matcher m = Syntax.matcher(markup);
    if (m.find()) {
      to = m.group(1);
    } else {
      throw LiquidException.syntax(options.locale().t("errors.syntax.capture"));
    }
  }

  @Override
  public Object render(Context context) {
    String output = Values.toS(super.render(context));
    List<Map<String, Object>> scopes = context.scopes();
    scopes.get(scopes.size() - 1).put(to, output);
    context.resourceLimits().assignScore += output.length();
    return "";
  }

  @Override
  public boolean isBlank() {
    return true;
  }
}
