package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/InstanceOfNotDefinedError.ts (SUSHI 3.21.1)
public class InstanceOfNotDefinedError extends RuntimeException implements WithSource {
  public final String instanceName;
  public final String instanceOf;
  public final SourceInfo sourceInfo;

  public InstanceOfNotDefinedError(String instanceName, String instanceOf, SourceInfo sourceInfo) {
    super("InstanceOf " + s(instanceOf) + " not found for " + s(instanceName));
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
