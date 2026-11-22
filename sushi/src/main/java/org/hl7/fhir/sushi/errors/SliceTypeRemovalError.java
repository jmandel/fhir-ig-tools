package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/SliceTypeRemovalError.ts (SUSHI 3.21.1)
public class SliceTypeRemovalError extends RuntimeException {
  public final String rootPath;
  public final String sliceId;

  public SliceTypeRemovalError(String rootPath, String sliceId) {
    super("Type constraint on " + s(rootPath) + " would eliminate all types on slice " + s(sliceId));
    this.rootPath = rootPath;
    this.sliceId = sliceId;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
