package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/TypeNotFoundError.ts (SUSHI 3.21.1)
public class TypeNotFoundError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.type");
  public final String unfoundType;

  public TypeNotFoundError(String unfoundType) {
    super("No definition for the type \"" + s(unfoundType) + "\" could be found.");
    this.unfoundType = unfoundType;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
