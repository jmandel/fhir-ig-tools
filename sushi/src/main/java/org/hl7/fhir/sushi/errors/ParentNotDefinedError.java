package org.hl7.fhir.sushi.errors;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/ParentNotDefinedError.ts (SUSHI 3.21.1)
public class ParentNotDefinedError extends RuntimeException implements Annotated, WithSource {
  public final List<String> specReferences = List.of(
      "https://www.hl7.org/fhir/R4/profiling.html#resources");
  public final String childName;
  public final String parentName;
  public final SourceInfo sourceInfo;

  public ParentNotDefinedError(String childName, String parentName, SourceInfo sourceInfo) {
    super("Parent " + s(parentName) + " not found for " + s(childName));
    this.childName = childName;
    this.parentName = parentName;
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
