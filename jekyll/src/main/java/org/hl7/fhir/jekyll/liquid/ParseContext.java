package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.List;

/** Port of liquid-4.0.4/lib/liquid/parse_context.rb. */
public class ParseContext {
  private final ParseOptions options;
  private final I18n locale;
  private final Environment environment;
  private final List<LiquidException> warnings = new ArrayList<>();
  private Integer lineNumber;
  private boolean trimWhitespace;
  private int depth;
  private boolean partial;
  private ErrorMode errorMode;

  public ParseContext(ParseOptions options) {
    this.options = options == null ? new ParseOptions() : options;
    this.locale = this.options.locale() != null ? this.options.locale() : new I18n();
    this.environment = this.options.environment() != null ? this.options.environment() : Environment.defaultEnvironment();
    this.depth = 0;
    partial(false);
  }

  public ParseOptions options() {
    return options;
  }

  /** Ruby {@code parse_context[option_key]}. */
  public Object get(String key) {
    return options.option(key);
  }

  public I18n locale() {
    return locale;
  }

  public Environment environment() {
    return environment;
  }

  public List<LiquidException> warnings() {
    return warnings;
  }

  public ErrorMode errorMode() {
    return errorMode;
  }

  public Integer lineNumber() {
    return lineNumber;
  }

  public void lineNumber(Integer n) {
    this.lineNumber = n;
  }

  public boolean trimWhitespace() {
    return trimWhitespace;
  }

  public void trimWhitespace(boolean b) {
    this.trimWhitespace = b;
  }

  public int depth() {
    return depth;
  }

  public void depth(int d) {
    this.depth = d;
  }

  public boolean partial() {
    return partial;
  }

  /** Ruby {@code partial=}; partials reuse the template options (no include_options_blacklist). */
  public void partial(boolean value) {
    this.partial = value;
    this.errorMode = options.errorMode() != null ? options.errorMode() : ErrorMode.LAX;
  }
}
