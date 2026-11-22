package org.hl7.fhir.sushi.importer.config;

// port of sushi/src/import/ensureConfigurationFile.ts (SUSHI 3.21.1)
public final class EnsureConfigurationFile {
  private EnsureConfigurationFile() {}

  /** Path of root/sushi-config.yaml or .yml if it exists (logged), else null (deprecated config.yaml: error). */
  public static String ensureConfiguration(String root) {
    throw new UnsupportedOperationException("TODO WP-3");
  }
}
