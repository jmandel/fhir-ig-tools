package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/MismatchedBindingTypeError.ts (SUSHI 3.21.1)
public class MismatchedBindingTypeError extends RuntimeException {
  public final String id;
  public final String path;
  public final String correctedType;

  public MismatchedBindingTypeError(String id, String path, String correctedType) {
    super("Cannot " + ("CodeSystem".equals(correctedType) ? "assign" : "bind") + " " + s(id) + " at path " + s(path) + ". A " + s(correctedType) + " must be used.");
    this.id = id;
    this.path = path;
    this.correctedType = correctedType;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
