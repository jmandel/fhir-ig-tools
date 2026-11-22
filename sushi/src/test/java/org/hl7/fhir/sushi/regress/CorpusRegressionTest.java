package org.hl7.fhir.sushi.regress;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.js.Json;
import org.junit.jupiter.api.Test;

/** Regression tests for differences found by the corpus differential (WP-10's corpus-diff), one per root cause. */
class CorpusRegressionTest {
  /** HL7__phd: FSHOnly config without version -> TS assigns undefined and omits "version"; Java wrote null. */
  @SuppressWarnings("unchecked")
  @Test void sdVersionFromAbsentConfigVersionIsOmitted() {
    StructureDefinition sd = StructureDefinition.fromJSON(Json.parse(
        "{\"resourceType\":\"StructureDefinition\",\"id\":\"x\",\"version\":\"1.0\",\"snapshot\":{\"element\":[{\"id\":\"Patient\",\"path\":\"Patient\"}]}}"));
    sd.setVersion(null);
    Map<String, Object> j = (Map<String, Object>) sd.toJSON(false);
    assertFalse(j.containsKey("version"), Json.stringify(j));
    assertNull(sd.getVersion());
    sd.setVersion("2.0");
    assertEquals("2.0", ((Map<String, Object>) sd.toJSON(false)).get("version"));
  }

  /** HL7__davinci-ecdx: an unresolvable #latest fails with FPL's RedundantRegistryClient message. */
  @Test void unresolvableLatestUsesFplMessage() {
    var fake = new org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.FakePackageProvider();
    fake.resolveError = new RuntimeException("offline");
    var loader = new org.hl7.fhir.sushi.fhirdefs.BasePackageLoader(new org.hl7.fhir.sushi.fhirdefs.InMemoryPackageDB(),
        fake, null, 0);
    RuntimeException e = assertThrows(RuntimeException.class, () -> loader.loadPackage("hl7.fhir.uv.xver-r4.r4", "latest"));
    assertEquals("Failed to resolve version for hl7.fhir.uv.xver-r4.r4#latest", e.getMessage());
  }

  /** HL7__cds-hooks-library: #latest = the registry's dist-tags.latest (2.0.1), not core's highest (3.0.0-ballot). Network. */
  @Test void latestIsRegistryDistTagLatest() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("sushi.network.tests"), "set -Dsushi.network.tests=true");
    assertEquals("2.0.1", new org.hl7.fhir.sushi.fhirdefs.CorePackageProvider(
        java.nio.file.Files.createTempDirectory("pc").toString()).resolveLatest("hl7.fhir.uv.cds-hooks"));
  }
}
