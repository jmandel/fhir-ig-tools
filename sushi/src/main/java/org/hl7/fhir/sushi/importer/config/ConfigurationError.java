package org.hl7.fhir.sushi.importer.config;

/**
 * The plain {@code new Error(msg)} importConfiguration throws after logging ("Invalid configuration YAML", "Minimal
 * config not met"); app.ts only reports its message if nothing was logged yet.
 */
public class ConfigurationError extends RuntimeException {
  public ConfigurationError(String message) {
    super(message);
  }
}
