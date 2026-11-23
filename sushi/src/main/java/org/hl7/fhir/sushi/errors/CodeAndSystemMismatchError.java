package org.hl7.fhir.sushi.errors;

import java.util.List;
import java.util.Map;

// port of sushi/src/errors/CodeAndSystemMismatchError.ts (SUSHI 3.21.1)
public class CodeAndSystemMismatchError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/datatypes.html#Range");
  public final Map<String, ?> q1;
  public final Map<String, ?> q2;

  public CodeAndSystemMismatchError(Map<String, ?> q1, Map<String, ?> q2) {
    super("The quantities must have matching codes and systems, but their codes are " + s(q1.get("code")) + " and " + s(q2.get("code")) + " and their systems are " + s(q1.get("system")) + " and " + s(q2.get("system")) + ".");
    this.q1 = q1;
    this.q2 = q2;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
