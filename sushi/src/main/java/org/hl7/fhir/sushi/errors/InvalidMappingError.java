package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidMappingError.ts (SUSHI 3.21.1)
public class InvalidMappingError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "https://www.hl7.org/fhir/elementdefinition-definitions.html#ElementDefinition.mapping");

  public InvalidMappingError() {
    super("Invalid mapping, mapping.identity and mapping.map are 1..1 and must be set. Ensure mapping rules specify map: {path} -> {map}");
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
