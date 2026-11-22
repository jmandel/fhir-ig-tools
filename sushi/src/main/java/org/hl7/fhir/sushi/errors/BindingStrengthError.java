package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/BindingStrengthError.ts (SUSHI 3.21.1)
public class BindingStrengthError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/terminologies.html#strength");
  public final String foundStrength;
  public final String requestedStrength;

  public BindingStrengthError(String foundStrength, String requestedStrength) {
    super("Cannot override " + s(foundStrength) + " binding with " + s(requestedStrength) + " binding.");
    this.foundStrength = foundStrength;
    this.requestedStrength = requestedStrength;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
