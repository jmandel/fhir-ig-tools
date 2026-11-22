package org.hl7.fhir.sushi.importer.config;

import org.hl7.fhir.sushi.fshtypes.Configuration;

// port of sushi/src/import/loadConfigurationFromIgResource.ts (SUSHI 3.21.1)
public final class LoadConfigurationFromIgResource {
  private LoadConfigurationFromIgResource() {}

  /** Extracts a FSHOnly configuration from the IG resource under {@code igRoot}, or null (TS null). */
  public static Configuration loadConfigurationFromIgResource(String igRoot) {
    throw new UnsupportedOperationException("TODO WP-3");
  }
}
