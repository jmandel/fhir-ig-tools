package org.hl7.fhir.jekyll.liquid;

import java.util.HashMap;
import java.util.Map;

/** The options hash given to Ruby {@code Template.parse(source, options)}. */
public class ParseOptions {
  private ErrorMode errorMode;
  private boolean lineNumbers;
  private Environment environment;
  private I18n locale;
  private final Map<String, Object> extra = new HashMap<>();

  public ParseOptions errorMode(ErrorMode m) {
    this.errorMode = m;
    return this;
  }

  /** null = the default (Ruby {@code Template.error_mode}, :lax). */
  public ErrorMode errorMode() {
    return errorMode;
  }

  public ParseOptions lineNumbers(boolean b) {
    this.lineNumbers = b;
    return this;
  }

  public boolean lineNumbers() {
    return lineNumbers;
  }

  public ParseOptions environment(Environment env) {
    this.environment = env;
    return this;
  }

  public Environment environment() {
    return environment;
  }

  public ParseOptions locale(I18n l) {
    this.locale = l;
    return this;
  }

  public I18n locale() {
    return locale;
  }

  /** Arbitrary extra options (Ruby {@code parse_context[:key]}). */
  public ParseOptions option(String key, Object value) {
    extra.put(key, value);
    return this;
  }

  public Object option(String key) {
    return extra.get(key);
  }
}
