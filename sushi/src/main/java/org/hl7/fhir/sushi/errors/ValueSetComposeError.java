package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/ValueSetComposeError.ts (SUSHI 3.21.1)
public class ValueSetComposeError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/valueset-definitions.html#ValueSet.compose.include");

  public ValueSetComposeError(String valueSetName) {
    super("ValueSet " + s(valueSetName) + " must contain at least one inclusion rule or be empty.");
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
