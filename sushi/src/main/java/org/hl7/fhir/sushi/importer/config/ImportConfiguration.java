package org.hl7.fhir.sushi.importer.config;

import java.util.Map;
import org.hl7.fhir.sushi.fshtypes.Configuration;

// port of sushi/src/import/importConfiguration.ts (SUSHI 3.21.1)
/**
 * Imports sushi-config.yaml (YAML text, or the already-parsed YAML as a JS-model object) into the normalized
 * {@link Configuration}. Logs errors/warnings like TS and throws {@code Error('Invalid configuration YAML')} /
 * {@code Error('Minimal config not met')} (as {@link ConfigurationError}) where TS throws.
 */
public final class ImportConfiguration {
  private ImportConfiguration() {}

  /** TS importConfiguration(yaml: string, file). */
  public static Configuration importConfiguration(String yaml, String file) {
    throw new UnsupportedOperationException("TODO WP-3");
  }

  /** TS importConfiguration(yaml: YAMLConfiguration, file): {@code yaml} is the parsed YAML (JsObject). */
  public static Configuration importConfiguration(Map<String, Object> yaml, String file) {
    throw new UnsupportedOperationException("TODO WP-3");
  }
}
