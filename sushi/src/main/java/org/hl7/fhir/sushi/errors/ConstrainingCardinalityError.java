package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/ConstrainingCardinalityError.ts (SUSHI 3.21.1)
public class ConstrainingCardinalityError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/profiling.html#cardinality");
  public final Number originalMin;
  public final String originalMax;
  public final Number newMin;
  public final String newMax;

  public ConstrainingCardinalityError(Number originalMin, String originalMax, Number newMin, String newMax) {
    super("Cannot constrain cardinality to " + s(newMin) + ".." + s(newMax) + ", as it does not fit within the original " + s(originalMin) + ".." + s(originalMax) + " cardinality.");
    this.originalMin = originalMin;
    this.originalMax = originalMax;
    this.newMin = newMin;
    this.newMax = newMax;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
