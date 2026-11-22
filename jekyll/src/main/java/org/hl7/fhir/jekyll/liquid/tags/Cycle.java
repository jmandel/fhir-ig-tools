package org.hl7.fhir.jekyll.liquid.tags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Evaluable;
import org.hl7.fhir.jekyll.liquid.Expression;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.RubyRange;
import org.hl7.fhir.jekyll.liquid.Tag;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/cycle.rb. */
public class Cycle extends Tag {
  static final Pattern SimpleSyntax = Pattern.compile("\\A(?:" + Liquid.QUOTED_FRAGMENT + ")+", Pattern.UNIX_LINES);
  static final Pattern NamedSyntax = Pattern.compile("(?s)\\A(" + "(?:" + Liquid.QUOTED_FRAGMENT + ")" + ")\\s*\\:\\s*(.*)", Pattern.UNIX_LINES);
  static final Pattern VarSyntax = Pattern.compile("\\s*(" + "(?:" + Liquid.QUOTED_FRAGMENT + ")" + ")\\s*", Pattern.UNIX_LINES);

  private final List<Object> variables;
  private final Object name;

  public Cycle(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    Matcher m;
    if ((m = NamedSyntax.matcher(markup)).find()) {
      variables = variablesFromString(m.group(2));
      name = Expression.parse(m.group(1));
    } else if (SimpleSyntax.matcher(markup).find()) {
      variables = variablesFromString(markup);
      name = inspectKey(variables);
    } else {
      throw LiquidException.syntax(options.locale().t("errors.syntax.cycle"));
    }
  }

  /**
   * Ruby uses {@code @variables.to_s} (Array#inspect) as the group key. Literals inspect
   * deterministically; VariableLookups inspect with their object address, i.e. are unique per tag.
   */
  private String inspectKey(List<Object> vars) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vars.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      Object v = vars.get(i);
      if (v instanceof Evaluable) {
        sb.append("#<").append(v.getClass().getSimpleName()).append(':').append(System.identityHashCode(this)).append('>');
      } else if (v instanceof RubyRange || v instanceof Expression.MethodLiteral) {
        sb.append(v instanceof RubyRange ? v.toString() : "#<MethodLiteral:" + System.identityHashCode(v) + ">");
      } else {
        sb.append(Values.inspect(v));
      }
    }
    return sb.append(']').toString();
  }

  public List<Object> variables() {
    return variables;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object render(Context context) {
    Map<Object, Object> cycle = (Map<Object, Object>) context.registers().computeIfAbsent("cycle", k -> new HashMap<>());
    return context.stack(() -> {
      Object key = context.evaluate(name);
      long iteration = Values.toLong(cycle.get(key));
      Object result = iteration >= 0 && iteration < variables.size() ? context.evaluate(variables.get((int) iteration)) : null;
      iteration += 1;
      if (iteration >= variables.size()) {
        iteration = 0;
      }
      cycle.put(key, iteration);
      return result;
    });
  }

  private static List<Object> variablesFromString(String markup) {
    List<Object> out = new ArrayList<>();
    for (Object var : org.hl7.fhir.jekyll.liquid.RubyStrings.split(markup, ",")) {
      Matcher m = VarSyntax.matcher((String) var);
      Object e = m.find() ? Expression.parse(m.group(1)) : null;
      if (e != null) {
        out.add(e);
      }
    }
    return out;
  }
}
