package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/ValueSetFilterOperatorError.ts (SUSHI 3.21.1)
public class ValueSetFilterOperatorError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "https://www.hl7.org/fhir/valueset-filter-operator.html");
  public final String value;

  public ValueSetFilterOperatorError(String value) {
    super("Operator \"" + s(value) + "\" not available in value set filter.");
    this.value = value;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
