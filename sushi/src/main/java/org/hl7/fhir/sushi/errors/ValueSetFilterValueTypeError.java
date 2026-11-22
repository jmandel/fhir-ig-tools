package org.hl7.fhir.sushi.errors;

import java.util.List;
import java.util.stream.Collectors;
import org.hl7.fhir.sushi.fshtypes.VsOperator;

// port of sushi/src/errors/ValueSetFilterValueTypeError.ts (SUSHI 3.21.1)
public class ValueSetFilterValueTypeError extends RuntimeException {
  public final VsOperator operator;
  /** TS expectedType: {@code 'string' | 'regex' | 'boolean' | 'code'}. */
  public final List<String> expectedTypes;
  public final List<String> allowedStrings;

  public ValueSetFilterValueTypeError(VsOperator operator, List<String> expectedTypes) {
    this(operator, expectedTypes, List.of());
  }

  public ValueSetFilterValueTypeError(VsOperator operator, List<String> expectedTypes, List<String> allowedStrings) {
    this(operator, expectedTypes, allowedStrings == null ? List.of() : allowedStrings, true);
  }

  private ValueSetFilterValueTypeError(VsOperator operator, List<String> types, List<String> allowed, boolean x) {
    super("Not applying value set filter: operator \"" + Errors.str(operator) + "\" requires "
        + getExpectedTypes(types, allowed) + ".");
    this.operator = operator;
    this.expectedTypes = types;
    this.allowedStrings = allowed;
  }

  private static String getExpectedTypes(List<String> types, List<String> allowedStrings) {
    String formattedAllowedStrings = allowedStrings.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(" or "));
    String allowedStringsClause = formattedAllowedStrings.isEmpty() ? "" : " or a string value of " + formattedAllowedStrings;
    return "a " + String.join(" or ", types) + " value" + allowedStringsClause;
  }
}
