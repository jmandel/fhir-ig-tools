package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidElementForSlicingError.ts (SUSHI 3.21.1)
public class InvalidElementForSlicingError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/elementdefinition-definitions.html#ElementDefinition.slicing");
  public final String path;

  public InvalidElementForSlicingError(String path) {
    super("Cannot slice element '" + s(path) + "' since FHIR only allows slicing on choice elements (e.g., value[x]) or elements with max > 1");
    this.path = path;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
