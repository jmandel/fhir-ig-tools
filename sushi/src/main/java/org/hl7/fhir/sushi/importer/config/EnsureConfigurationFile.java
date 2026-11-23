package org.hl7.fhir.sushi.importer.config;

import java.nio.file.Path;
import org.hl7.fhir.sushi.utils.Fs;
import org.hl7.fhir.sushi.utils.FshLogger;

// port of sushi/src/import/ensureConfigurationFile.ts (SUSHI 3.21.1)
public final class EnsureConfigurationFile {
  private EnsureConfigurationFile() {}

  /**
   * Checks for a sushi-config.yaml or sushi-config.yml file. If it finds one, it returns its path, otherwise null
   * (TS undefined).
   *
   * @param root the root path of the FSH Tank
   */
  public static String ensureConfiguration(String root) {
    String configPath = null;
    for (String name : new String[] {"sushi-config.yaml", "sushi-config.yml"}) {
      String p = NodePath.join(root, name);
      if (Fs.exists(p)) {
        configPath = p;
        break;
      }
    }
    if (configPath != null) {
      // The config already exists, so return it
      FshLogger.logger.info("Using configuration file: " + Path.of(configPath).toAbsolutePath().normalize());
    }
    if (configPath == null) {
      for (String name : new String[] {"config.yaml", "config.yml"}) {
        String p = NodePath.join(root, name);
        if (Fs.exists(p)) {
          FshLogger.logger.error("Use of " + NodePath.basename(p) + " is no longer supported. Please rename "
              + "configuration file to \"sushi-config.yaml\" in order to use this configuration file.");
          break;
        }
      }
    }
    return configPath; // Return the path to the config or null if it wasn't found
  }
}
