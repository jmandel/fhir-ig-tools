package org.hl7.fhir.sushi.fhirdefs;

// port of fhir-package-loader 2.2.4 src/errors/* (message texts verbatim)
public final class FplErrors {
  private FplErrors() {}

  public static class CurrentPackageLoadError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final String fullPackageName;

    public CurrentPackageLoadError(String fullPackageName) {
      super("The package " + fullPackageName + " is not available on https://build.fhir.org/ig/qas.json, so no "
          + "current version can be loaded");
      this.fullPackageName = fullPackageName;
    }
  }

  public static class IncorrectWildcardVersionFormatError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final String packageName;

    public IncorrectWildcardVersionFormatError(String packageName, String version) {
      super("Incorrect version format for package " + packageName + ": " + version
          + ". Wildcard should only be used to specify patch versions.");
      this.packageName = packageName;
    }
  }

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

  public static class LatestVersionUnavailableError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final String packageName;
    public final boolean isPatchWildCard;

    public LatestVersionUnavailableError(String packageName, boolean isPatchWildCard) {
      super("Latest " + (isPatchWildCard ? "patch " : "") + "version of package " + packageName
          + " could not be determined from the package registry");
      this.packageName = packageName;
      this.isPatchWildCard = isPatchWildCard;
    }
  }
}
