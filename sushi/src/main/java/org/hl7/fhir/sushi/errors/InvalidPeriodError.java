package org.hl7.fhir.sushi.errors;

import java.util.List;
import java.util.Map;

// port of sushi/src/errors/InvalidPeriodError.ts (SUSHI 3.21.1)
public class InvalidPeriodError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/datatypes-definitions.html#Period");
  public final Map<String, ?> badPeriod;

  public InvalidPeriodError(Map<String, ?> badPeriod) {
    super("The start " + s(badPeriod.get("start")) + " cannot come before the end " + s(badPeriod.get("end")) + ".");
    this.badPeriod = badPeriod;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
