package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/DuplicateSliceError.ts (SUSHI 3.21.1)
public class DuplicateSliceError extends RuntimeException {

  public DuplicateSliceError(String structDef, String element, String slice) {
    super("Slice named " + s(slice) + " already exists on element " + s(element) + " of " + s(structDef));
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
