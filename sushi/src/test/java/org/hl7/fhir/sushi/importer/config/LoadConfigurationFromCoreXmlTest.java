package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A legacy ImplementationGuide in FHIR XML is read with core's XML parser (docs/core-divergences.md C6), not with
 * fhir-tool's lenient xmlToObj as in TS SUSHI; replaces the three TS tests on invalid XML fixtures that
 * LoadConfigurationFromIgResourceTest keeps disabled.
 */
class LoadConfigurationFromCoreXmlTest {
  @BeforeEach
  void reset() {
    loggerSpy.reset();
    FshLogger.logger.resetForTest();
  }

  @Test
  void extractsConfigurationFromValidFhirXml() {
    JsObject config = LoadConfigurationFromIgResource.loadConfigurationFromIgResourceJs("src/test/resources/fhirxml/ig-XML-valid");
    assertNotNull(config);
    assertEquals("http://example.org/ImplementationGuide", config.get("url"));
    assertEquals("http://example.org", config.get("canonical"));
    assertEquals("fhir.test.ig", config.get("packageId"));
    assertEquals(List.of("4.0.1"), config.get("fhirVersion"));
    assertEquals(List.of(Map.of("packageId", "foo.bar", "version", "1.2.3"), Map.of("packageId", "bar.foo", "version", "current")),
        config.get("dependencies"));
  }

  @Test
  void invalidFhirXmlIsNotTakenForAnIgResource() {
    // TS SUSHI's ig-XML fixture: no FHIR namespace, dependsOn before fhirVersion
    String tsFixture = ImportConfigurationTest.fixture("fixtures", "ig-XML");
    assertNull(LoadConfigurationFromIgResource.loadConfigurationFromIgResourceJs(tsFixture));
  }

  @Test
  void twoValidIgResourcesGiveNoConfiguration() {
    assertNull(LoadConfigurationFromIgResource.loadConfigurationFromIgResource("src/test/resources/fhirxml/ig-XML-and-ig-JSON-valid"));
    assertTrue(loggerSpy.getLastMessage("error").contains("Multiple possible ImplementationGuide resources"));
  }
}
