package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/SlicingNotDefinedError.ts (SUSHI 3.21.1)
public class SlicingNotDefinedError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/profiling.html#slicing");
  public final String id;
  public final String sliceName;

  public SlicingNotDefinedError(String id, String sliceName) {
    super("Cannot create " + s(sliceName) + " slice. No slicing found for " + s(id) + ".");
    this.id = id;
    this.sliceName = sliceName;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
