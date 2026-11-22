package org.hl7.fhir.sushi.errors;

import java.util.List;
import org.hl7.fhir.sushi.js.Js;

// port of sushi/src/errors/MismatchedTypeError.ts (SUSHI 3.21.1)
public class MismatchedTypeError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.fixed_x_",
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.pattern_x_");
  public final String valueType;
  public final Object value;
  public final String elementType;

  public MismatchedTypeError(String valueType, Object value, String elementType) {
    super("Cannot assign " + s(valueType) + " value: " + Js.toStr(value) + ". Value does not match element type: " + s(elementType));
    this.valueType = valueType;
    this.value = value;
    this.elementType = elementType;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
