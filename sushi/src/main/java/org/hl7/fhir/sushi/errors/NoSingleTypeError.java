package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/NoSingleTypeError.ts (SUSHI 3.21.1)
public class NoSingleTypeError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.fixed_x_",
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.pattern_x_");
  public final String type;

  public NoSingleTypeError(String type) {
    super("Cannot assign " + s(type) + " value on this element since this element does not have a single type");
    this.type = type;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
