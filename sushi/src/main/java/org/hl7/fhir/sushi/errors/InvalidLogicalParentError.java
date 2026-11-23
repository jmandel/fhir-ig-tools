package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/InvalidLogicalParentError.ts (SUSHI 3.21.1)
public class InvalidLogicalParentError extends RuntimeException implements WithSource {
  public final String childName;
  public final String parentName;
  public final SourceInfo sourceInfo;

  public InvalidLogicalParentError(String childName, String parentName, SourceInfo sourceInfo) {
    super("Invalid parent " + s(parentName) + " specified for logical model " + s(childName) + ". The parent of a logical model must be Element, Base, another logical model, a resource, or a type.");
    this.childName = childName;
    this.parentName = parentName;
    this.sourceInfo = sourceInfo;
  }

  @Override public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
