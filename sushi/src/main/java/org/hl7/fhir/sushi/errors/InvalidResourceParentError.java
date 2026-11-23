package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/InvalidResourceParentError.ts (SUSHI 3.21.1)
public class InvalidResourceParentError extends RuntimeException implements WithSource {
  public final String childName;
  public final String parentName;
  public final SourceInfo sourceInfo;

  public InvalidResourceParentError(String childName, String parentName, SourceInfo sourceInfo) {
    super("Invalid parent " + s(parentName) + " specified for resource " + s(childName) + ". The parent of a resource must be Resource or DomainResource.");
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
