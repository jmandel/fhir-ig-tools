package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/AbstractInstanceOfError.ts (SUSHI 3.21.1)
public class AbstractInstanceOfError extends RuntimeException implements WithSource {
  public final String instanceName;
  public final String instanceOf;
  public final SourceInfo sourceInfo;

  public AbstractInstanceOfError(String instanceName, String instanceOf, SourceInfo sourceInfo) {
    super("The definition for " + s(instanceName) + " is an instance of an abstract resource: " + s(instanceOf));
    this.instanceName = instanceName;
    this.instanceOf = instanceOf;
    this.sourceInfo = sourceInfo;
  }

  @Override public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
