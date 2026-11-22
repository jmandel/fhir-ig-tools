package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/InvalidElementAccessError.ts (SUSHI 3.21.1)
public class InvalidElementAccessError extends RuntimeException {
  public final String path;

  public InvalidElementAccessError(String path) {
    super("Cannot directly access differential or snapshot with path: " + s(path) + ". Elements should be targeted for modification by their path.");
    this.path = path;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
