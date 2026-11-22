package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.common.js.Js;

// port of sushi/src/errors/ParentDeclaredAsIdError.ts (SUSHI 3.21.1)
public class ParentDeclaredAsIdError extends RuntimeException implements WithSource {
  public final String type;
  public final String name;
  public final String id;
  public final SourceInfo sourceInfo;
  public final String fhirResourceUrl;

  public ParentDeclaredAsIdError(String type, String name, String id, SourceInfo sourceInfo, String fhirResourceUrl) {
    super(s(type) + " \"" + s(name) + "\" cannot declare \"" + s(id) + "\" as both Parent and Id." + (Js.truthy(fhirResourceUrl) ? " It looks like the parent is an external resource; use its URL (e.g., " + fhirResourceUrl + ")." : ""));
    this.type = type;
    this.name = name;
    this.id = id;
    this.sourceInfo = sourceInfo;
    this.fhirResourceUrl = fhirResourceUrl;
  }

  @Override public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
