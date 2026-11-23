package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/ClosedSlicingError.ts (SUSHI 3.21.1)
public class ClosedSlicingError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/profiling.html#reslicing");
  public final String id;
  public final List<String> sliceNames;

  public ClosedSlicingError(String id, List<String> sliceNames) {
    super("Cannot add " + Errors.join(", ", sliceNames) + " to " + s(id) + " since its slicing was closed by a parent definition. New slices can only be added by the definition that closes the slicing.");
    this.id = id;
    this.sliceNames = sliceNames;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
