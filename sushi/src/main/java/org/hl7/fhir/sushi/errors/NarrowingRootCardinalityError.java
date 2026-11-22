package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/NarrowingRootCardinalityError.ts (SUSHI 3.21.1)
public class NarrowingRootCardinalityError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/profiling.html#cardinality");
  public final String rootElement;
  public final String existingSlice;
  public final Number newMin;
  public final String newMax;
  public final Number sliceMin;
  public final String sliceMax;

  public NarrowingRootCardinalityError(String rootElement, String existingSlice, Number newMin, String newMax, Number sliceMin, String sliceMax) {
    super("Cardinality on " + s(rootElement) + " cannot be narrowed to " + s(newMin) + ".." + s(newMax) + " due to existing slice " + s(existingSlice) + " with cardinality " + s(sliceMin) + ".." + s(sliceMax) + ".");
    this.rootElement = rootElement;
    this.existingSlice = existingSlice;
    this.newMin = newMin;
    this.newMax = newMax;
    this.sliceMin = sliceMin;
    this.sliceMax = sliceMax;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
