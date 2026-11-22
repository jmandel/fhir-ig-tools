package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/InvalidRangeValueError.ts (SUSHI 3.21.1)
public class InvalidRangeValueError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/datatypes-definitions.html#Range");
  public final Number low;
  public final Number high;

  public InvalidRangeValueError(Number low, Number high) {
    super("The low must be <= high, but low " + s(low) + " is > high " + s(high) + ".");
    this.low = low;
    this.high = high;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
