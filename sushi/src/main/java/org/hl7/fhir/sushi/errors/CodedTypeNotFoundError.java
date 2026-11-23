package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/CodedTypeNotFoundError.ts (SUSHI 3.21.1)
public class CodedTypeNotFoundError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.binding");
  public final List<String> foundTypes;

  public CodedTypeNotFoundError(List<String> foundTypes) {
    super("Cannot bind value set to " + Errors.join(",", foundTypes) + "; must be coded (code, Coding, CodeableConcept, Quantity, CodeableReference), the data types (string, uri), or a logical model with the #can-bind characteristic.");
    this.foundTypes = foundTypes;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
