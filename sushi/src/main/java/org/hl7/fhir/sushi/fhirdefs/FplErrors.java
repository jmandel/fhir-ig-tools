package org.hl7.fhir.sushi.fhirdefs;

// port of fhir-package-loader 2.2.4 src/errors/InvalidResourceError (message text verbatim); FPL's registry/version
// errors are not ported: version resolution and download are core's
public final class FplErrors {
  private FplErrors() {}

  public static class InvalidResourceError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final String resourcePath;
    public final String reason;

    public InvalidResourceError(String resourcePath, String reason) {
      super("The resource at " + resourcePath + " is not a valid FHIR resource: " + reason + ".");
      this.resourcePath = resourcePath;
      this.reason = reason;
    }
  }
}
