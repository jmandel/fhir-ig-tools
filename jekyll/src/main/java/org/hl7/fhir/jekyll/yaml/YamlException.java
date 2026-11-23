package org.hl7.fhir.jekyll.yaml;

/**
 * A Ruby exception raised while loading YAML, with its Ruby class name: {@code Psych::SyntaxError}
 * (see {@link PsychSyntaxError}), {@code TypeError} (e.g. {@code <<: } merging a non-hash),
 * {@code ArgumentError} (e.g. {@code Float()} rejecting {@code 1_.5}), or {@code RuntimeError}.
 * {@link #getMessage()} is the Ruby message; {@link #toString()} is "Class: message" like the oracle.
 */
public class YamlException extends RuntimeException {
  private final String rubyClass;

  public YamlException(String rubyClass, String message) {
    super(message);
    this.rubyClass = rubyClass;
  }

  public String rubyClass() {
    return rubyClass;
  }

  @Override
  public String toString() {
    return rubyClass + ": " + getMessage();
  }
}
