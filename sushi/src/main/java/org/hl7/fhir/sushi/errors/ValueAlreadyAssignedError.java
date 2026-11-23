package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/ValueAlreadyAssignedError.ts (SUSHI 3.21.1)
public class ValueAlreadyAssignedError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.fixed_x_",
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.pattern_x_");
  public final Object requestedValue;
  public final String elementType;
  public final Object foundValue;

  public ValueAlreadyAssignedError(Object requestedValue, String elementType, Object foundValue) {
    super("Cannot assign " + s(requestedValue) + " to this element; a different " + s(elementType) + " is already assigned: " + s(foundValue) + ".");
    this.requestedValue = requestedValue;
    this.elementType = elementType;
    this.foundValue = foundValue;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
