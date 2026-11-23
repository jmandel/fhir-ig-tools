package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/SlicingDefinitionError.ts (SUSHI 3.21.1)
public class SlicingDefinitionError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/profiling.html#reslicing");
  public final String property;
  public final Object oldValue;
  public final Object newValue;

  public SlicingDefinitionError(String property, Object oldValue, Object newValue) {
    super("Cannot constraint slicing property '" + s(property) + "' from " + s(oldValue) + " to " + s(newValue) + ".");
    this.property = property;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
