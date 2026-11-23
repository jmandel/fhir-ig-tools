package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/ValidationError.ts (SUSHI 3.21.1)
public class ValidationError extends RuntimeException {
  /** TS ErrorSeverity: {@code 'error' | 'warn' | 'info'}. */
  public final String issue;
  public final String fshPath;
  public final String severity;

  public ValidationError(String issue, String fshPath) {
    this(issue, fshPath, "error");
  }

  public ValidationError(String issue, String fshPath, String severity) {
    super(Errors.str(fshPath) + ": " + Errors.str(issue));
    this.issue = issue;
    this.fshPath = fshPath;
    // TS default parameter: an explicit undefined also yields 'error'
    this.severity = severity == null ? "error" : severity;
  }
}
