package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;

/**
 * Shared beforeAll of sushi/test/fhirtypes/StructureDefinition.test.ts: the r4 test definitions (loaded once per JVM)
 * and the JSON of the SDs the tests start from. Test classes call {@link #defs()} etc. in their @BeforeEach.
 */
final class SdTestSupport {
  private SdTestSupport() {}

  private static TestFHIRDefinitions defs;
  private static TestFisher fisher;
  static Object jsonObservation;
  static Object jsonModifiedObservation;
  static Object jsonUsCoreObservation;
  static Object jsonPlanDefinition;

  /** TS beforeAll; skips the test when the TS fixtures are not available. */
  static synchronized void init() {
    assumeTrue(TestFHIRDefinitions.tsFixturesAvailable(), "TS fixtures not available");
    if (defs != null) return;
    defs = TestFHIRDefinitions.getTestFHIRDefinitions(true, TestFHIRDefinitions.testDefsPath("r4-definitions"));
    fisher = new TestFisher().withFHIR(defs);
    jsonObservation = defs.fishForFHIR("Observation", FishableType.Resource);
    jsonModifiedObservation = Js.cloneDeep(jsonObservation);
    ((JsObject) jsonModifiedObservation).put("_baseDefinition", Json.parse("{\"id\": \"baseDefinition-extension\","
        + " \"extension\": [{\"url\": \"http://hl7.org/fhir/StructureDefinition/structuredefinition-codegen-super\","
        + " \"valueString\": \"MetadataResource\"}]}"));
    jsonUsCoreObservation = defs.fishForFHIR("us-core-observation-lab", FishableType.Profile);
    jsonPlanDefinition = defs.fishForFHIR("PlanDefinition", FishableType.Resource);
  }

  static TestFHIRDefinitions defs() {
    init();
    return defs;
  }

  static TestFisher fisher() {
    init();
    return fisher;
  }
}
