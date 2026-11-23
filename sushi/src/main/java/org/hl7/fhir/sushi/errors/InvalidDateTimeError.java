package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidDateTimeError.ts (SUSHI 3.21.1)
public class InvalidDateTimeError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/datatypes.html#dateTime");
  public final String nonDate;

  public InvalidDateTimeError(String nonDate) {
    super("The string " + s(nonDate) + " does not represent a valid FHIR dateTime.");
    this.nonDate = nonDate;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
