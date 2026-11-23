package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.testhelpers.LoggerSpy;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.Test;

/**
 * loadConfigurationFromIgResource vs TS (scripts/oracle/ig-resource-oracle.js): the corpus IG without
 * sushi-config.yaml (HL7 termchangeset-ig, XML IG resource with comments) and the TS fixtures; configuration object
 * (tagged JSON) and log lines identical.
 */
class IgResourceOracleTest {
  @SuppressWarnings("unchecked")
  @Test void igResourceConfigsMatchTs() throws Exception {
    Path oracle = ConfigOracle.dir().resolve("igres-oracle.json");
    assumeTrue(Files.isRegularFile(oracle), "no oracle data");
    Map<String, Object> out = (Map<String, Object>) Json.parse(Files.readString(oracle));
    StringBuilder report = new StringBuilder();
    for (Map.Entry<String, Object> e : out.entrySet()) {
      if (!Files.isDirectory(Path.of(e.getKey()))) continue;
      Map<String, Object> ts = (Map<String, Object>) e.getValue();
      loggerSpy.reset();
      FshLogger.logger.resetForTest();
      Object config = LoadConfigurationFromIgResource.loadConfigurationFromIgResourceJs(e.getKey());
      List<Object> logs = new ArrayList<>();
      for (LoggerSpy.Log l : loggerSpy.getAllLogs()) {
        if (!l.level().equals("debug")) logs.add(new JsObject().with("level", l.level()).with("message", l.message()));
      }
      String j = Json.stringify(ConfigOracle.dump(config)), t = Json.stringify(ts.get("config"));
      if (!j.equals(t)) report.append(e.getKey()).append(": config ").append(ConfigOracle.firstDiff(
          ConfigOracle.dump(config), ts.get("config"), "")).append('\n');
      if (!Json.stringify(logs).equals(Json.stringify(ts.get("logs")))) {
        report.append(e.getKey()).append(": logs TS ").append(Json.stringify(ts.get("logs"))).append(" Java ")
            .append(Json.stringify(logs)).append('\n');
      }
    }
    assertEquals("", report.toString());
  }
}
