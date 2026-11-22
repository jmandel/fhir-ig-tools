package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Port of liquid-4.0.4/lib/liquid/template.rb (profiling omitted). */
public class Template {
  private Document root;
  private List<LiquidException> warnings = Collections.emptyList();
  private List<LiquidException> errors = new ArrayList<>();
  private boolean rethrowErrors;
  private Environment environment;
  private final ResourceLimits resourceLimits = new ResourceLimits();

  public static Template parse(String source) {
    return parse(source, new ParseOptions());
  }

  public static Template parse(String source, ParseOptions options) {
    Template t = new Template();
    return t.parseSource(source, new ParseContext(options));
  }

  /** Ruby {@code Template.parse(source, parse_context)} (used for include partials). */
  public static Template parse(String source, ParseContext parseContext) {
    Template t = new Template();
    return t.parseSource(source, parseContext);
  }

  private Template parseSource(String source, ParseContext parseContext) {
    this.environment = parseContext.environment();
    this.root = Document.parse(new Tokenizer(source, parseContext.options().lineNumbers()), parseContext);
    this.warnings = parseContext.warnings();
    return this;
  }

  public Document root() {
    return root;
  }

  /** Ruby {@code Template#warnings}: syntax errors recovered from in :warn mode. */
  public List<LiquidException> warnings() {
    return warnings;
  }

  /** Ruby {@code Template#errors}: errors handled during the last render. */
  public List<LiquidException> errors() {
    return errors;
  }

  public ResourceLimits resourceLimits() {
    return resourceLimits;
  }

  /** Ruby {@code render(assigns_hash)}. */
  public String render(Map<String, Object> assigns) {
    Context c = new Context(List.of(assigns != null ? assigns : new LinkedHashMap<String, Object>()), new LinkedHashMap<>(),
        new LinkedHashMap<>(), rethrowErrors, resourceLimits, environment);
    return render(c);
  }

  /** Ruby {@code render(context)}: errors are rendered inline as "Liquid error: ...". */
  public String render(Context context) {
    if (root == null) {
      return "";
    }
    if (rethrowErrors) {
      context.rethrowErrors(true);
    }
    // Retrying a render resets resource usage
    context.resourceLimits().reset();
    try {
      return Values.toS(root.render(context));
    } catch (LiquidException e) {
      if (e.kind() == LiquidException.Kind.MEMORY) {
        return context.handleError(e, null);
      }
      throw e;
    } finally {
      errors = context.errors();
    }
  }

  /** Ruby {@code render!}: errors are raised. Like Ruby, this makes the template re-raise from now on. */
  public String renderStrict(Context context) {
    rethrowErrors = true;
    return render(context);
  }

  public String renderStrict(Map<String, Object> assigns) {
    rethrowErrors = true;
    return render(assigns);
  }
}
