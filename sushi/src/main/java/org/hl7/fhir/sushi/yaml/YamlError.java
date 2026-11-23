package org.hl7.fhir.sushi.yaml;

/**
 * An error {@code YAML.parse} of the npm package yaml@1.10 throws: {@link #name} is its JS class name
 * ({@code YAMLSyntaxError}, {@code YAMLSemanticError}, {@code YAMLReferenceError}); {@link #getMessage()} is its
 * {@code message} (yaml 1.10 does not add positions to the message unless prettyErrors is set, and SUSHI does not
 * set it).
 */
public final class YamlError extends RuntimeException {
  public final String name;

  public YamlError(String name, String message) {
    super(message, null, false, false);
    this.name = name;
  }

  static YamlError syntax(String message) {
    return new YamlError("YAMLSyntaxError", message);
  }

  static YamlError semantic(String message) {
    return new YamlError("YAMLSemanticError", message);
  }

  static YamlError reference(String message) {
    return new YamlError("YAMLReferenceError", message);
  }
}
