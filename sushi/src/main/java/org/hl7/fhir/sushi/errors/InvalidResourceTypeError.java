package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidResourceTypeError.ts (SUSHI 3.21.1)
public class InvalidResourceTypeError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "https://www.hl7.org/fhir/resourcelist.html");
  public final String resourceType;
  public final String elementType;

  public InvalidResourceTypeError(String resourceType, String elementType) {
    super("A resourceType of " + s(resourceType) + " cannot be set on an element of type " + s(elementType) + ".");
    this.resourceType = resourceType;
    this.elementType = elementType;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
