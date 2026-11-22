package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/ElementAlreadyDefinedError.ts (SUSHI 3.21.1)
public class ElementAlreadyDefinedError extends RuntimeException {
  public final String elementId;
  public final String definitionId;

  public ElementAlreadyDefinedError(String elementId, String definitionId) {
    super("Cannot define element " + s(elementId) + " on " + s(definitionId) + " because it has already been defined");
    this.elementId = elementId;
    this.definitionId = definitionId;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
