package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/CannotResolvePathError.ts (SUSHI 3.21.1)
public class CannotResolvePathError extends RuntimeException {
  public final String path;

  public CannotResolvePathError(String path) {
    super("The element or path you referenced does not exist: " + s(path));
    this.path = path;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
