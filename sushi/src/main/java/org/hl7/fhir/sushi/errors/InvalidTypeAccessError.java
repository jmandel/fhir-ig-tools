package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/InvalidTypeAccessError.ts (SUSHI 3.21.1)
public class InvalidTypeAccessError extends RuntimeException {
  public InvalidTypeAccessError() {
    super("Cannot directly change type. StructureDefinitions will naturally inherit their Parent type.");
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
