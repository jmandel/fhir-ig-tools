package org.hl7.fhir.jekyll.liquid.tags;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Tag;
import org.hl7.fhir.jekyll.liquid.Variable;

/** Port of liquid-4.0.4/lib/liquid/tags/assign.rb. */
public class Assign extends Tag {
  static final Pattern Syntax = Pattern.compile("(?s)(" + "(?:" + Liquid.VARIABLE_SIGNATURE + ")" + "+)\\s*=\\s*(.*)\\s*", Pattern.UNIX_LINES);

  private final String to;
  private final Variable from;

  public Assign(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    Matcher m = Syntax.matcher(markup);
    if (m.find()) {
      to = m.group(1);
      from = new Variable(m.group(2), options);
    } else {
      throw LiquidException.syntax(options.locale().t("errors.syntax.assign"));
    }
  }

  public String to() {
    return to;
  }

  public Variable from() {
    return from;
  }

  @Override
  public Object render(Context context) {
    Object val = from.render(context);
    List<Map<String, Object>> scopes = context.scopes();
    scopes.get(scopes.size() - 1).put(to, val);
    context.resourceLimits().assignScore += assignScoreOf(val);
    return "";
  }

  @Override
  public boolean isBlank() {
    return true;
  }

  private static long assignScoreOf(Object val) {
    if (val instanceof String) {
      return ((String) val).length();
    } else if (val instanceof List || val instanceof Map) {
      long sum = 1;
      Iterable<?> it = val instanceof List ? (List<?>) val : ((Map<?, ?>) val).entrySet();
      for (Object child : it) {
        sum += assignScoreOf(child);
      }
      return sum;
    }
    return 1;
  }
}
