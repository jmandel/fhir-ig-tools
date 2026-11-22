package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidFHIRIdError.ts (SUSHI 3.21.1)
public class InvalidFHIRIdError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "https://www.hl7.org/fhir/datatypes.html#id");
  public final String badId;

  public InvalidFHIRIdError(String badId) {
    super("The string \"" + s(badId) + "\" does not represent a valid FHIR id. FHIR ids may contain any combination of upper- or lower-case ASCII letters ('A'..'Z', and 'a'..'z'), numerals ('0'..'9'), '-' and '.', with a length limit of 64 characters.");
    this.badId = badId;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
