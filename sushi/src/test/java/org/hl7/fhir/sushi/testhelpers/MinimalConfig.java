package org.hl7.fhir.sushi.testhelpers;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.Configuration;

// port of sushi/test/utils/minimalConfig.ts (SUSHI 3.21.1)
/**
 * TS exports one shared object; tests that need a variant spread it ({...minimalConfig}). Java returns a fresh copy per
 * call, which is what every TS use amounts to (none mutates the shared object intentionally).
 */
public final class MinimalConfig {
  private MinimalConfig() {}

  public static Configuration minimalConfig() {
    Configuration c = new Configuration();
    c.filePath = "sushi-config.yaml";
    c.id = "fhir.us.minimal";
    c.canonical = "http://hl7.org/fhir/us/minimal";
    c.name = "MinimalIG";
    c.status = "draft";
    c.version = "1.0.0";
    c.fhirVersion = new ArrayList<>(List.of("4.0.1"));
    return c;
  }
}
