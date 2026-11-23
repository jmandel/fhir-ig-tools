package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/MultipleStandardsStatusError.ts (SUSHI 3.21.1)
public class MultipleStandardsStatusError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/extension-structuredefinition-standards-status.html");
  public final String element;

  public MultipleStandardsStatusError(String element) {
    super("Cannot apply multiple standards status on " + s(element));
    this.element = element;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
