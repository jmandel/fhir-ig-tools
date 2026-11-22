package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidMustSupportError.ts (SUSHI 3.21.1)
public class InvalidMustSupportError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/profiling.html#mustsupport",
      "https://www.hl7.org/fhir/elementdefinition.html#interpretation");

  public InvalidMustSupportError(String structDef, String element) {
    super("The MustSupport flag is not permitted on element " + s(element) + " of " + s(structDef) + " (allowed only in profiles and extensions).");
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
