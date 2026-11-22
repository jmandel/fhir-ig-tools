package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/InvalidCanonicalUrlError.ts (SUSHI 3.21.1)
public class InvalidCanonicalUrlError extends RuntimeException {
  public final String entityName;

  public InvalidCanonicalUrlError(String entityName) {
    super("Cannot use canonical URL of " + s(entityName) + " because it does not exist. Be sure that " + s(entityName) + " exists and it has a URL.");
    this.entityName = entityName;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
