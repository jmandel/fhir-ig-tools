package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.VsOperator;

// port of sushi/src/errors/ValueSetFilterMissingValueError.ts (SUSHI 3.21.1)
public class ValueSetFilterMissingValueError extends RuntimeException {
  public final VsOperator operator;

  public ValueSetFilterMissingValueError(VsOperator operator) {
    super("Not applying value set filter: missing value for operator \"" + s(operator) + "\".");
    this.operator = operator;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
