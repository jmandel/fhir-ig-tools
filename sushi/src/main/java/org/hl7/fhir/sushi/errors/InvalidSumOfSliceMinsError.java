package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidSumOfSliceMinsError.ts (SUSHI 3.21.1)
public class InvalidSumOfSliceMinsError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://www.hl7.org/fhir/profiling.html#slice-cardinality");
  public final Number sumMins;
  public final String max;
  public final String slicedElementId;

  public InvalidSumOfSliceMinsError(Number sumMins, String max, String slicedElementId) {
    super("The sum of mins of slices must be <= max of sliced element, but sum of mins (" + s(sumMins) + ") > max (" + s(max) + ") of " + s(slicedElementId) + ".");
    this.sumMins = sumMins;
    this.max = max;
    this.slicedElementId = slicedElementId;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
