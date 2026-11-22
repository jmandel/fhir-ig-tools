package org.hl7.fhir.jekyll.liquid.tags;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Expression;
import org.hl7.fhir.jekyll.liquid.FileSystem;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.RubyError;
import org.hl7.fhir.jekyll.liquid.RubyStrings;
import org.hl7.fhir.jekyll.liquid.Tag;
import org.hl7.fhir.jekyll.liquid.Template;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/include.rb (Liquid's own include; Jekyll replaces it). */
public class Include extends Tag {
  static final String QF = "(?:" + Liquid.QUOTED_FRAGMENT + ")";
  static final Pattern Syntax = Pattern.compile("(" + QF + "+)(\\s+(?:with|for)\\s+(" + QF + "+))?", Pattern.UNIX_LINES);

  private final Object templateNameExpr;
  private final Object variableNameExpr;
  private final Map<String, Object> attributes = new LinkedHashMap<>();

  public Include(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    Matcher m = Syntax.matcher(markup);
    if (m.find()) {
      String templateName = m.group(1);
      String variableName = m.group(3);
      variableNameExpr = variableName != null ? Expression.parse(variableName) : null;
      templateNameExpr = Expression.parse(templateName);
      for (String[] kv : Liquid.scanGroups(Liquid.TagAttributes, markup)) {
        attributes.put(kv[0], Expression.parse(kv[1]));
      }
    } else {
      throw LiquidException.syntax(options.locale().t("errors.syntax.include"));
    }
  }

  @Override
  public Object render(Context context) {
    Object templateName = context.evaluate(templateNameExpr);
    if (!Values.truthy(templateName)) {
      throw LiquidException.argument(options().locale().t("errors.argument.include"));
    }
    Template partial = loadCachedPartial(templateName, context);
    if (!(templateName instanceof String)) {
      throw RubyError.noMethod("split", templateName);
    }
    List<Object> parts = RubyStrings.split((String) templateName, "/");
    String contextVariableName = parts.isEmpty() ? null : (String) parts.get(parts.size() - 1);

    Object variable = variableNameExpr != null ? context.evaluate(variableNameExpr) : context.findVariable(templateName, false);

    String oldTemplateName = context.templateName();
    boolean oldPartial = context.partial();
    try {
      context.templateName((String) templateName);
      context.partial(true);
      return context.stack(() -> {
        for (Map.Entry<String, Object> e : attributes.entrySet()) {
          context.set(e.getKey(), context.evaluate(e.getValue()));
        }
        if (variable instanceof List) {
          StringBuilder sb = new StringBuilder();
          for (Object var : (List<?>) variable) {
            context.set(contextVariableName, var);
            sb.append(partial.render(context));
          }
          return sb.toString();
        }
        context.set(contextVariableName, variable);
        return partial.render(context);
      });
    } finally {
      context.templateName(oldTemplateName);
      context.partial(oldPartial);
    }
  }

  @SuppressWarnings("unchecked")
  private Template loadCachedPartial(Object templateName, Context context) {
    Object cp = context.registers().get("cached_partials");
    Map<Object, Template> cachedPartials = cp instanceof Map ? (Map<Object, Template>) cp : new HashMap<>();
    Template cached = cachedPartials.get(templateName);
    if (cached != null) {
      return cached;
    }
    String source = readTemplateFromFileSystem(context);
    Template partial;
    try {
      parseContext.partial(true);
      partial = Template.parse(source, parseContext);
    } finally {
      parseContext.partial(false);
    }
    cachedPartials.put(templateName, partial);
    context.registers().put("cached_partials", cachedPartials);
    return partial;
  }

  private String readTemplateFromFileSystem(Context context) {
    Object fs = context.registers().get("file_system");
    FileSystem fileSystem = fs instanceof FileSystem ? (FileSystem) fs : context.environment().fileSystem();
    return fileSystem.readTemplateFile(context.evaluate(templateNameExpr));
  }
}
