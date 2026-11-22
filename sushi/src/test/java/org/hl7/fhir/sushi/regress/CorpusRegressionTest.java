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
}
