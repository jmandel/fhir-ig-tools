package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/MissingSnapshotError.ts (SUSHI 3.21.1)
public class MissingSnapshotError extends RuntimeException {
  public final String url;

  public MissingSnapshotError(String url) {
    super("Structure Definition " + s(url) + " is missing a snapshot. Snapshot is required for import.");
    this.url = url;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
