package org.hl7.fhir.sushi.errors;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/ParentNotProvidedError.ts (SUSHI 3.21.1)
public class ParentNotProvidedError extends RuntimeException implements Annotated, WithSource {
  public final List<String> specReferences = List.of(
      "https://www.hl7.org/fhir/R4/profiling.html#resources");
  public final String defnName;
  public final SourceInfo sourceInfo;

  public ParentNotProvidedError(String defnName, SourceInfo sourceInfo) {
    super("The definition for " + s(defnName) + " does not include a Parent");
    this.defnName = defnName;
    this.sourceInfo = sourceInfo;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  @Override public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
