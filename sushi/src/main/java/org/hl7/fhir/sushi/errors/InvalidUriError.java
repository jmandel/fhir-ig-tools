package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/InvalidUriError.ts (SUSHI 3.21.1)
public class InvalidUriError extends RuntimeException {
  public final String notUri;

  public InvalidUriError(String notUri) {
    super("Resolved value \"" + s(notUri) + "\" is not a valid URI.");
    this.notUri = notUri;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
