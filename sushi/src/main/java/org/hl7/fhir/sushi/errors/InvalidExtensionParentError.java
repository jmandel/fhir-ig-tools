package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/InvalidExtensionParentError.ts (SUSHI 3.21.1)
public class InvalidExtensionParentError extends RuntimeException implements WithSource {
  public final String childName;
  public final String parentName;
  public final SourceInfo sourceInfo;

  public InvalidExtensionParentError(String childName, String parentName, SourceInfo sourceInfo) {
    super("Invalid parent " + s(parentName) + " specified for extension " + s(childName) + ". The parent of an extension must be the base Extension or another defined extension.");
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
