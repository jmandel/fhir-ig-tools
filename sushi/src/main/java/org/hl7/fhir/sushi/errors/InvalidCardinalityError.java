package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidCardinalityError.ts (SUSHI 3.21.1)
public class InvalidCardinalityError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.min");
  public final Number min;
  public final String max;

  public InvalidCardinalityError(Number min, String max) {
    super("The min must be <= max, but min " + s(min) + " is > max " + s(max) + ".");
    this.min = min;
    this.max = max;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
