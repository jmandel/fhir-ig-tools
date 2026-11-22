package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/ValueConflictsWithClosedSlicingError.ts (SUSHI 3.21.1)
public class ValueConflictsWithClosedSlicingError extends RuntimeException {
  public final Object requestedValue;

  public ValueConflictsWithClosedSlicingError(Object requestedValue) {
    super("Cannot assign " + s(requestedValue) + " to this element since it conflicts with all values of the closed slicing.");
    this.requestedValue = requestedValue;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
