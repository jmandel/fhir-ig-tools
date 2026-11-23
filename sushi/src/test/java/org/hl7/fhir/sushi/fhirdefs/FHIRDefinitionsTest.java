package org.hl7.fhir.sushi.fhirdefs;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getLocalVirtualPackage;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

// port of sushi/test/fhirdefs/FHIRDefinitions.test.ts (SUSHI 3.21.1)
@DisplayName("FHIRDefinitions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FHIRDefinitionsTest {
  static final FishableType Resource = FishableType.Resource, Logical = FishableType.Logical,
      Type = FishableType.Type, Profile = FishableType.Profile, Extension = FishableType.Extension,
      ValueSet = FishableType.ValueSet, CodeSystem = FishableType.CodeSystem, Instance = FishableType.Instance;

  FHIRDefinitions defs;
  FHIRDefinitions r4bDefs;
  FHIRDefinitions r5Defs;
  String r4bPkg;
  String r5Pkg;

  @BeforeAll
  void beforeAll() {
    Assumptions.assumeTrue(TestFHIRDefinitions.tsFixturesAvailable(), "SUSHI test fixtures not available");
    defs = FHIRDefinitions.createFHIRDefinitions();
    // Add the R5toR4 resources. This mirrors what happens in Processing.ts.
    Map<String, Object> r5forR4Map = new LinkedHashMap<>();
    R5DefsForR4.R5_DEFINITIONS_NEEDED_IN_R4.forEach(def -> r5forR4Map.put((String) def.get("id"), def));
    InMemoryVirtualPackage virtualR5forR4Package =
        new InMemoryVirtualPackage(InMemoryVirtualPackage.packageJSON("sushi-r5forR4", "1.0.0"), r5forR4Map);
    defs.loadVirtualPackage(virtualR5forR4Package);
    Map<String, Object> xverMap = new LinkedHashMap<>();
    xverMap.put("ext-R5-Observation.value", obj(
        "resourceType", "StructureDefinition",
        "id", "ext-R5-Observation.value",
        "url", "http://hl7.org/fhir/5.0/StructureDefinition/extension-Observation.value",
        "fhirVersion", "4.0.1",
        "kind", "complex-type",
        "type", "Extension",
        "baseDefinition", "http://hl7.org/fhir/StructureDefinition/Extension|4.0.1",
        "derivation", "constraint"));
    defs.loadVirtualPackage(new InMemoryVirtualPackage(
        InMemoryVirtualPackage.packageJSON("hl7.fhir.uv.xver-r5.r4", "0.1.0"), xverMap));
    defs.loadVirtualPackage(getLocalVirtualPackage(testDefsPath("r4-definitions")));
    r4bDefs = FHIRDefinitions.createFHIRDefinitions();
    r4bDefs.loadVirtualPackage(virtualR5forR4Package);
    DiskBasedVirtualPackage r4bVp = getLocalVirtualPackage(testDefsPath("r4b-definitions"));
    // TS expects sushi-test-1 / sushi-test-2 (module-level counter in a fresh jest module); our counter is shared
    // by all test classes in the JVM, so read the generated names back.
    r4bPkg = r4bVp.getPackageJSON().get("name") + "#" + r4bVp.getPackageJSON().get("version");
    r4bDefs.loadVirtualPackage(r4bVp);
    r5Defs = FHIRDefinitions.createFHIRDefinitions();
    DiskBasedVirtualPackage r5Vp = getLocalVirtualPackage(testDefsPath("r5-definitions"));
    r5Pkg = r5Vp.getPackageJSON().get("name") + "#" + r5Vp.getPackageJSON().get("version");
    r5Defs.loadVirtualPackage(r5Vp);
    // Add custom/predefined resources. This approximates SUSHI behavior by using the special package name.
    // TS builds these with fhirtypes StructureDefinition/ValueSet/CodeSystem (WP-5 skeletons here); only the keys
    // the tests look at are reproduced (StructureDefinition.toJSON(true) includes a snapshot).
    Map<String, Object> predefinedResourceMap = new LinkedHashMap<>();
    predefinedResourceMap.put("my-predefined-profile", obj(
        "resourceType", "StructureDefinition",
        "id", "my-predefined-profile",
        "url", "http://example.com/StructureDefinition/my-predefined-profile",
        "name", "MyPredefinedProfile",
        "snapshot", obj("element", List.of())));
    predefinedResourceMap.put("some-codes-vs", obj(
        "resourceType", "ValueSet",
        "name", "SomeCodes",
        "id", "some-codes",
        "url", "http://example.com/ValueSet/some-codes"));
    predefinedResourceMap.put("some-codes-cs", obj(
        "resourceType", "CodeSystem",
        "name", "SomeCodes",
        "id", "some-codes",
        "url", "http://example.com/CodeSystem/some-codes"));
    defs.loadVirtualPackage(new InMemoryVirtualPackage(
        InMemoryVirtualPackage.packageJSON(FHIRDefinitions.PREDEFINED_PACKAGE_NAME,
            FHIRDefinitions.PREDEFINED_PACKAGE_VERSION),
        predefinedResourceMap, (l, m) -> FshLogger.logger.logMessage(l, m), true));
  }

  @BeforeEach
  void beforeEach() {
    loggerSpy.reset();
  }

  // ------------------------------------------------------------------------------------------------ helpers

  static JsObject obj(Object... kv) {
    JsObject o = new JsObject();
    for (int i = 0; i < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
    return o;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> fish(FHIRDefinitions d, String item, FishableType... types) {
    return (Map<String, Object>) d.fishForFHIR(item, types);
  }

  /** Metadata as the JS object toEqual sees (undefined members ignored); key `abstract` for `_abstract`. */
  static Map<String, Object> meta(Metadata m) {
    if (m == null) return null;
    Map<String, Object> r = new LinkedHashMap<>();
    put(r, "id", m.id);
    put(r, "name", m.name);
    put(r, "sdType", m.sdType);
    put(r, "resourceType", m.resourceType);
    put(r, "url", m.url);
    put(r, "parent", m.parent);
    put(r, "imposeProfiles", m.imposeProfiles);
    put(r, "abstract", m._abstract);
    put(r, "version", m.version);
    put(r, "instanceUsage", m.instanceUsage);
    put(r, "canBeTarget", m.canBeTarget);
    put(r, "canBind", m.canBind);
    put(r, "resourcePath", m.resourcePath);
    return r;
  }

  static List<Map<String, Object>> metas(List<Metadata> l) {
    return l.stream().map(FHIRDefinitionsTest::meta).toList();
  }

  private static void put(Map<String, Object> r, String k, Object v) {
    if (v != null) r.put(k, v);
  }

  /** expected metadata object literal; null values = undefined (dropped) */
  static Map<String, Object> exp(Object... kv) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) put(r, (String) kv[i], kv[i + 1]);
    return r;
  }

  static void assertMatches(String regex, String actual) {
    assertNotNull(actual);
    assertTrue(Pattern.compile(regex).matcher(actual).find(), () -> "expected /" + regex + "/ to match: " + actual);
  }

  static void assertMatchesM(String regex, String actual) {
    assertNotNull(actual);
    assertTrue(Pattern.compile(regex, Pattern.MULTILINE).matcher(actual).find(),
        () -> "expected /" + regex + "/m to match: " + actual);
  }

  static String r4path(String file) {
    return "virtual:hl7.fhir.r4.core#4.0.1:" + testDefsPath("r4-definitions", "package", file);
  }

  // shared bodies of the xver tests (fishForFHIR/fishForMetadata/fishForMetadatas variants)
  interface Fisher {
    boolean found(String url);
  }

  void xverNeededPackage(Fisher f) {
    assertTrue(!f.found("http://hl7.org/fhir/1.0/StructureDefinition/extension-Patient.animal"));
    assertMatches("The extension http://hl7\\.org/fhir/1\\.0/StructureDefinition/extension-Patient\\.animal requires .+ "
        + "hl7\\.fhir\\.uv\\.xver-r2\\.r4 ", loggerSpy.getLastMessage("error"));
    assertTrue(!f.found("http://hl7.org/fhir/3.0/StructureDefinition/extension-Patient.animal"));
    assertMatches("The extension http://hl7\\.org/fhir/3\\.0/StructureDefinition/extension-Patient\\.animal requires .+ "
        + "hl7\\.fhir\\.uv\\.xver-r3\\.r4 ", loggerSpy.getLastMessage("error"));
    assertTrue(!f.found("http://hl7.org/fhir/4.3/StructureDefinition/extension-Evidence.assertion"));
    assertMatches("The extension http://hl7\\.org/fhir/4\\.3/StructureDefinition/extension-Evidence\\.assertion requires "
        + ".+ hl7\\.fhir\\.uv\\.xver-r4b\\.r4 ", loggerSpy.getLastMessage("error"));
  }

  void xverNotFoundInPackage(Fisher f) {
    assertTrue(!f.found("http://hl7.org/fhir/5.0/StructureDefinition/extension-Observation.foo"));
    assertMatches("The extension http://hl7\\.org/fhir/5\\.0/StructureDefinition/extension-Observation\\.foo was not found "
        + "in the extension package hl7\\.fhir\\.uv\\.xver-r5\\.r4\\.", loggerSpy.getLastMessage("error"));
    assertMatches("See: https://hl7\\.org/fhir/uv/xver-r5\\.r4/0\\.1\\.0/", loggerSpy.getLastMessage("error"));
  }

  void xverChoice(Fisher f) {
    assertTrue(f.found("http://hl7.org/fhir/5.0/StructureDefinition/extension-Observation.value[x]"));
    assertMatches("Cross-version extensions for choice elements should omit the \\[x\\] suffix.",
        loggerSpy.getLastMessage("warn"));
    assertMatchesM("Found URL:     http://hl7\\.org/fhir/5\\.0/StructureDefinition/extension-Observation\\.value\\[x\\]$",
        loggerSpy.getLastMessage("warn"));
    assertMatchesM("Corrected URL: http://hl7\\.org/fhir/5\\.0/StructureDefinition/extension-Observation\\.value$",
        loggerSpy.getLastMessage("warn"));
    assertTrue(loggerSpy.getAllLogs("error").isEmpty());
  }

  // ------------------------------------------------------------------------------------------------ #fishForFHIR()

  @Nested
  @DisplayName("#fishForFHIR()")
  class FishForFHIR {
    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR resources")
    void baseResources() {
      Map<String, Object> conditionByID = fish(defs, "Condition", Resource);
      assertEquals("http://hl7.org/fhir/StructureDefinition/Condition", conditionByID.get("url"));
      assertEquals("4.0.1", conditionByID.get("fhirVersion"));
      assertEquals(conditionByID, fish(defs, "http://hl7.org/fhir/StructureDefinition/Condition", Resource));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR logical models")
    void logicals() {
      Map<String, Object> m = fish(defs, "eLTSSServiceModel", Logical);
      assertEquals("http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel", m.get("url"));
      assertEquals("0.1.0", m.get("version"));
      assertEquals(m, fish(defs, "http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel", Logical));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR primitive types")
    void primitives() {
      Map<String, Object> b = fish(defs, "boolean", Type);
      assertEquals("http://hl7.org/fhir/StructureDefinition/boolean", b.get("url"));
      assertEquals("4.0.1", b.get("fhirVersion"));
      assertEquals(b, fish(defs, "http://hl7.org/fhir/StructureDefinition/boolean", Type));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR complex types")
    void complexTypes() {
      Map<String, Object> a = fish(defs, "Address", Type);
      assertEquals("http://hl7.org/fhir/StructureDefinition/Address", a.get("url"));
      assertEquals("4.0.1", a.get("fhirVersion"));
      assertEquals(a, fish(defs, "http://hl7.org/fhir/StructureDefinition/Address", Type));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR profiles")
    void profiles() {
      Map<String, Object> v = fish(defs, "vitalsigns", Profile);
      assertEquals("http://hl7.org/fhir/StructureDefinition/vitalsigns", v.get("url"));
      assertEquals("4.0.1", v.get("fhirVersion"));
      assertEquals(v, fish(defs, "observation-vitalsigns", Profile));
      assertEquals(v, fish(defs, "http://hl7.org/fhir/StructureDefinition/vitalsigns", Profile));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR profiles of logical models")
    void profilesOfLogicals() {
      Map<String, Object> s = fish(defs, "service-profile", Profile);
      assertEquals("http://hl7.org/fhir/some/example/StructureDefinition/ServiceProfile", s.get("url"));
      assertEquals("4.0.1", s.get("fhirVersion"));
      assertEquals(s, fish(defs, "ServiceProfile", Profile));
      assertEquals(s, fish(defs, "http://hl7.org/fhir/some/example/StructureDefinition/ServiceProfile", Profile));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR extensions")
    void extensions() {
      Map<String, Object> e = fish(defs, "patient-mothersMaidenName", Extension);
      assertEquals("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName", e.get("url"));
      assertEquals("4.0.1", e.get("fhirVersion"));
      assertEquals(e, fish(defs, "mothersMaidenName", Extension));
      assertEquals(e, fish(defs, "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName", Extension));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should log message that xver extensions package is needed for cross-version extension URLs without xver package loaded")
    void xverNeeded() {
      xverNeededPackage(u -> fish(defs, u, Extension) != null);
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should log message that cross-version extension was not found in xver package when correct xver package is loaded")
    void xverNotFound() {
      xverNotFoundInPackage(u -> fish(defs, u, Extension) != null);
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should log message that incorrect cross-version choice extension URL should be fixed and use the correct URL for resolution")
    void xverChoiceUrl() {
      xverChoice(u -> fish(defs, u, Extension) != null);
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR value sets")
    void valueSets() {
      Map<String, Object> vs = fish(defs, "allergyintolerance-clinical", ValueSet);
      assertEquals("http://hl7.org/fhir/ValueSet/allergyintolerance-clinical", vs.get("url"));
      assertEquals("4.0.1", vs.get("version"));
      assertEquals(vs, fish(defs, "AllergyIntoleranceClinicalStatusCodes", ValueSet));
      assertEquals(vs, fish(defs, "http://hl7.org/fhir/ValueSet/allergyintolerance-clinical", ValueSet));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find base FHIR code sytems")
    void codeSystems() {
      Map<String, Object> cs = fish(defs, "allergyintolerance-clinical", CodeSystem);
      assertEquals("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", cs.get("url"));
      assertEquals("4.0.1", cs.get("version"));
      assertEquals(cs, fish(defs, "AllergyIntoleranceClinicalStatusCodes", CodeSystem));
      assertEquals(cs, fish(defs, "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", CodeSystem));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find other types when the fished type is Type.Instance")
    void instance() {
      Map<String, Object> sp = fish(defs, "individual-gender", Instance);
      assertEquals("http://hl7.org/fhir/SearchParameter/individual-gender", sp.get("url"));
      assertEquals("4.0.1", sp.get("version"));
      assertEquals(sp, fish(defs, "gender", Instance));
      assertEquals(sp, fish(defs, "http://hl7.org/fhir/SearchParameter/individual-gender", Instance));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find the time-traveling R5 FHIR resources when R4 is loaded")
    void timeTravelR4Resources() {
      for (String r : List.of("ActorDefinition", "Requirements", "SubscriptionTopic", "TestPlan")) {
        Map<String, Object> res = fish(defs, r, Resource);
        assertNotNull(res);
        assertEquals(r, res.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, res.get("url"));
        assertEquals("5.0.0", res.get("fhirVersion"));
        assertEquals(true, res.get("_timeTraveler"));
        assertEquals(res, fish(defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find the time-traveling R5 FHIR types when R4 is loaded")
    void timeTravelR4Types() {
      for (String r : List.of("Base", "CodeableReference", "DataType")) {
        Map<String, Object> t = fish(defs, r, Type);
        assertNotNull(t);
        assertEquals(r, t.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, t.get("url"));
        assertEquals("5.0.0", t.get("fhirVersion"));
        assertEquals(true, t.get("_timeTraveler"));
        assertEquals(t, fish(defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find the time-traveling R5 FHIR resources when R4B is loaded")
    void timeTravelR4BResources() {
      for (String r : List.of("ActorDefinition", "Requirements", "TestPlan")) {
        Map<String, Object> res = fish(r4bDefs, r, Resource);
        assertNotNull(res);
        assertEquals(r, res.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, res.get("url"));
        assertEquals("5.0.0", res.get("fhirVersion"));
        assertEquals(true, res.get("_timeTraveler"));
        assertEquals(res, fish(r4bDefs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should overwrite time-traveling R5 FHIR resources that are in R4B when R4B is loaded")
    void overwriteR4BResources() {
      for (String r : List.of("SubscriptionTopic")) {
        Map<String, Object> res = fish(r4bDefs, r, Resource);
        assertNotNull(res);
        assertEquals(r, res.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, res.get("url"));
        assertEquals("4.3.0", res.get("fhirVersion"));
        assertNull(res.get("_timeTraveler"));
        assertEquals(res, fish(r4bDefs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find the time-traveling R5 FHIR types when R4B is loaded")
    void timeTravelR4BTypes() {
      for (String r : List.of("Base", "DataType")) {
        Map<String, Object> t = fish(r4bDefs, r, Type);
        assertNotNull(t);
        assertEquals(r, t.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, t.get("url"));
        assertEquals("5.0.0", t.get("fhirVersion"));
        assertEquals(true, t.get("_timeTraveler"));
        assertEquals(t, fish(r4bDefs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should overwrite time-traveling R5 FHIR types that are in R4B when R4B is loaded")
    void overwriteR4BTypes() {
      for (String r : List.of("CodeableReference")) {
        Map<String, Object> t = fish(r4bDefs, r, Type);
        assertNotNull(t);
        assertEquals(r, t.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, t.get("url"));
        assertEquals("4.3.0", t.get("fhirVersion"));
        assertNull(t.get("_timeTraveler"));
        assertEquals(t, fish(r4bDefs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should overwrite time-traveling R5 FHIR Resources when R5 is loaded")
    void overwriteR5Resources() {
      for (String r : List.of("ActorDefinition", "Requirements", "SubscriptionTopic", "TestPlan")) {
        Map<String, Object> res = fish(r5Defs, r, Resource);
        assertNotNull(res);
        assertEquals(r, res.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, res.get("url"));
        assertEquals("5.0.0", res.get("fhirVersion"));
        assertNull(res.get("_timeTraveler"));
        assertEquals(res, fish(r5Defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should overwrite time-traveling R5 FHIR Types when R5 is loaded")
    void overwriteR5Types() {
      // TS: ['Base', 'CodeableReference', , 'DataType'].forEach -- forEach skips the hole
      for (String r : List.of("Base", "CodeableReference", "DataType")) {
        Map<String, Object> t = fish(r5Defs, r, Type);
        assertNotNull(t);
        assertEquals(r, t.get("id"));
        assertEquals("http://hl7.org/fhir/StructureDefinition/" + r, t.get("url"));
        assertEquals("5.0.0", t.get("fhirVersion"));
        assertNull(t.get("_timeTraveler"));
        assertEquals(t, fish(r5Defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should find definitions by the enforced type order")
    void typeOrder() {
      // NOTE: There are two things with id allergyintolerance-clinical (the ValueSet and CodeSystem)
      assertEquals("ValueSet", fish(defs, "allergyintolerance-clinical", ValueSet, CodeSystem).get("resourceType"));
      assertEquals("ValueSet", fish(defs, "allergyintolerance-clinical", CodeSystem, ValueSet).get("resourceType"));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should not find the definition when the type is not requested")
    void notRequested() {
      assertNull(fish(defs, "Condition", Logical, Type, Profile, Extension, ValueSet, CodeSystem));
      assertNull(fish(defs, "boolean", Resource, Logical, Profile, Extension, ValueSet, CodeSystem));
      assertNull(fish(defs, "Address", Resource, Logical, Profile, Extension, ValueSet, CodeSystem));
      assertNull(fish(defs, "vitalsigns", Resource, Logical, Type, Extension, ValueSet, CodeSystem));
      assertNull(fish(defs, "patient-mothersMaidenName", Resource, Logical, Type, Profile, ValueSet, CodeSystem));
      assertNull(fish(defs, "allergyintolerance-clinical", Resource, Logical, Type, Profile, Extension));
      assertNull(fish(defs, "w3c-provenance-activity-type", Resource, Logical, Type, Profile, Extension, ValueSet));
      assertNull(fish(defs, "eLTSSServiceModel", Resource, Type, Profile, Extension, ValueSet, CodeSystem));
      assertNull(fish(defs, "individual-gender", Resource, Logical, Type, Profile, Extension, ValueSet, CodeSystem));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForFHIR() should globally find any definition")
    void global() {
      Map<String, Object> c = fish(defs, "Condition");
      assertEquals("resource", c.get("kind"));
      assertEquals("4.0.1", c.get("fhirVersion"));
      assertEquals(c, fish(defs, "http://hl7.org/fhir/StructureDefinition/Condition"));

      Map<String, Object> b = fish(defs, "boolean");
      assertEquals("primitive-type", b.get("kind"));
      assertEquals("4.0.1", b.get("fhirVersion"));
      assertEquals(b, fish(defs, "http://hl7.org/fhir/StructureDefinition/boolean"));

      Map<String, Object> a = fish(defs, "Address");
      assertEquals("complex-type", a.get("kind"));
      assertEquals("4.0.1", a.get("fhirVersion"));
      assertEquals(a, fish(defs, "http://hl7.org/fhir/StructureDefinition/Address"));

      Map<String, Object> v = fish(defs, "vitalsigns");
      assertEquals("Observation", v.get("type"));
      assertEquals("resource", v.get("kind"));
      assertEquals("constraint", v.get("derivation"));
      assertEquals("4.0.1", v.get("fhirVersion"));
      assertEquals(v, fish(defs, "observation-vitalsigns"));
      assertEquals(v, fish(defs, "http://hl7.org/fhir/StructureDefinition/vitalsigns"));

      Map<String, Object> e = fish(defs, "patient-mothersMaidenName");
      assertEquals("Extension", e.get("type"));
      assertEquals("4.0.1", e.get("fhirVersion"));
      assertEquals(e, fish(defs, "mothersMaidenName"));
      assertEquals(e, fish(defs, "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName"));

      // When doing a non-type-specific search, we favor the ValueSet
      Map<String, Object> vs = fish(defs, "allergyintolerance-clinical");
      assertEquals("ValueSet", vs.get("resourceType"));
      assertEquals("4.0.1", vs.get("version"));
      assertEquals(vs, fish(defs, "AllergyIntoleranceClinicalStatusCodes"));
      assertEquals(vs, fish(defs, "http://hl7.org/fhir/ValueSet/allergyintolerance-clinical"));

      Map<String, Object> cs = fish(defs, "w3c-provenance-activity-type");
      assertEquals("CodeSystem", cs.get("resourceType"));
      assertEquals("4.0.1", cs.get("version"));
      assertEquals(cs, fish(defs, "W3cProvenanceActivityType"));
      assertEquals(cs, fish(defs, "http://hl7.org/fhir/w3c-provenance-activity-type"));

      Map<String, Object> l = fish(defs, "eLTSSServiceModel");
      assertEquals("logical", l.get("kind"));
      assertEquals("specialization", l.get("derivation"));
      assertEquals(l, fish(defs, "http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel"));

      Map<String, Object> sp = fish(defs, "individual-gender");
      assertEquals("http://hl7.org/fhir/SearchParameter/individual-gender", sp.get("url"));
      assertEquals("4.0.1", sp.get("version"));
      assertEquals(sp, fish(defs, "gender"));
      assertEquals(sp, fish(defs, "http://hl7.org/fhir/SearchParameter/individual-gender"));
    }
  }

  // ------------------------------------------------------------------------------------------------ #fishForMetadata()

  static Map<String, Object> conditionMeta() {
    return exp("abstract", false, "id", "Condition", "name", "Condition", "sdType", "Condition",
        "url", "http://hl7.org/fhir/StructureDefinition/Condition", "version", "4.0.1",
        "parent", "http://hl7.org/fhir/StructureDefinition/DomainResource", "resourceType", "StructureDefinition",
        "resourcePath", r4path("StructureDefinition-Condition.json"));
  }

  static Map<String, Object> booleanMeta() {
    return exp("abstract", false, "id", "boolean", "name", "boolean", "sdType", "boolean",
        "url", "http://hl7.org/fhir/StructureDefinition/boolean", "version", "4.0.1",
        "parent", "http://hl7.org/fhir/StructureDefinition/Element", "resourceType", "StructureDefinition",
        "resourcePath", r4path("StructureDefinition-boolean.json"));
  }

  static Map<String, Object> addressMeta() {
    return exp("abstract", false, "id", "Address", "name", "Address", "sdType", "Address",
        "url", "http://hl7.org/fhir/StructureDefinition/Address", "version", "4.0.1",
        "parent", "http://hl7.org/fhir/StructureDefinition/Element", "resourceType", "StructureDefinition",
        "resourcePath", r4path("StructureDefinition-Address.json"));
  }

  static Map<String, Object> vitalsMeta() {
    return exp("abstract", false, "id", "vitalsigns", "name", "observation-vitalsigns", "sdType", "Observation",
        "url", "http://hl7.org/fhir/StructureDefinition/vitalsigns", "version", "4.0.1",
        "parent", "http://hl7.org/fhir/StructureDefinition/Observation", "resourceType", "StructureDefinition",
        "resourcePath", r4path("StructureDefinition-vitalsigns.json"));
  }

  static Map<String, Object> maidenMeta() {
    return exp("abstract", false, "id", "patient-mothersMaidenName", "name", "mothersMaidenName",
        "sdType", "Extension", "url", "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName",
        "version", "4.0.1", "parent", "http://hl7.org/fhir/StructureDefinition/Extension",
        "resourceType", "StructureDefinition", "resourcePath", r4path("StructureDefinition-patient-mothersMaidenName.json"));
  }

  static Map<String, Object> eltssMeta() {
    return exp("abstract", false, "id", "eLTSSServiceModel", "name", "ELTSSServiceModel",
        "sdType", "eLTSSServiceModel", "url", "http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel",
        "version", "0.1.0", "parent", "http://hl7.org/fhir/StructureDefinition/Element",
        "resourceType", "StructureDefinition", "canBeTarget", false, "canBind", false,
        "resourcePath", r4path("StructureDefinition-eLTSSServiceModel.json"));
  }

  static Map<String, Object> allergyVsMeta() {
    return exp("id", "allergyintolerance-clinical", "name", "AllergyIntoleranceClinicalStatusCodes",
        "url", "http://hl7.org/fhir/ValueSet/allergyintolerance-clinical", "version", "4.0.1",
        "resourceType", "ValueSet", "resourcePath", r4path("ValueSet-allergyintolerance-clinical.json"));
  }

  static Map<String, Object> allergyCsMeta() {
    return exp("id", "allergyintolerance-clinical", "name", "AllergyIntoleranceClinicalStatusCodes",
        "url", "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", "version", "4.0.1",
        "resourceType", "CodeSystem", "resourcePath", r4path("CodeSystem-allergyintolerance-clinical.json"));
  }

  static Map<String, Object> genderMeta() {
    return exp("id", "individual-gender", "name", "gender",
        "resourcePath", r4path("SearchParameter-individual-gender.json"), "resourceType", "SearchParameter",
        "url", "http://hl7.org/fhir/SearchParameter/individual-gender", "version", "4.0.1");
  }

  @Nested
  @DisplayName("#fishForMetadata()")
  class FishForMetadata {
    Map<String, Object> md(FHIRDefinitions d, String item, FishableType... t) {
      return meta(d.fishForMetadata(item, t));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR resources")
    void baseResources() {
      Map<String, Object> c = md(defs, "Condition", Resource);
      assertEquals(conditionMeta(), c);
      assertEquals(c, md(defs, "http://hl7.org/fhir/StructureDefinition/Condition", Resource));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find profiles with declared imposeProfiles")
    void imposeProfiles() {
      Map<String, Object> n = md(defs, "named-and-gendered-patient", Profile);
      assertEquals(exp("abstract", false, "id", "named-and-gendered-patient", "name", "NamedAndGenderedPatient",
          "sdType", "Patient", "url", "http://example.org/impose/StructureDefinition/named-and-gendered-patient",
          "version", "0.1.0", "parent", "http://hl7.org/fhir/StructureDefinition/Patient",
          "resourceType", "StructureDefinition",
          "imposeProfiles", List.of("http://example.org/impose/StructureDefinition/named-patient",
              "http://example.org/impose/StructureDefinition/gendered-patient"),
          "resourcePath", r4path("StructureDefinition-named-and-gendered-patient.json")), n);
      assertEquals(n, md(defs, "NamedAndGenderedPatient", Profile));
      assertEquals(n, md(defs, "http://example.org/impose/StructureDefinition/named-and-gendered-patient", Profile));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR logical models")
    void logicals() {
      Map<String, Object> l = md(defs, "eLTSSServiceModel", Logical);
      assertEquals(eltssMeta(), l);
      assertEquals(l, md(defs, "http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel", Logical));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR primitive types")
    void primitives() {
      Map<String, Object> b = md(defs, "boolean", Type);
      assertEquals(booleanMeta(), b);
      assertEquals(b, md(defs, "http://hl7.org/fhir/StructureDefinition/boolean", Type));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR complex types")
    void complexTypes() {
      Map<String, Object> a = md(defs, "Address", Type);
      assertEquals(addressMeta(), a);
      assertEquals(a, md(defs, "http://hl7.org/fhir/StructureDefinition/Address", Type));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR profiles")
    void profiles() {
      Map<String, Object> v = md(defs, "vitalsigns", Profile);
      assertEquals(vitalsMeta(), v);
      assertEquals(v, md(defs, "observation-vitalsigns", Profile));
      assertEquals(v, md(defs, "http://hl7.org/fhir/StructureDefinition/vitalsigns", Profile));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR extensions")
    void extensions() {
      Map<String, Object> e = md(defs, "patient-mothersMaidenName", Extension);
      assertEquals(maidenMeta(), e);
      assertEquals(e, md(defs, "mothersMaidenName", Extension));
      assertEquals(e, md(defs, "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName", Extension));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should log message that xver extensions package is needed for cross-version extension URLs without xver package loaded")
    void xverNeeded() {
      xverNeededPackage(u -> defs.fishForMetadata(u, Extension) != null);
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should log message that cross-version extension was not found in xver package when correct xver package is loaded")
    void xverNotFound() {
      xverNotFoundInPackage(u -> defs.fishForMetadata(u, Extension) != null);
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should log message that incorrect cross-version choice extension URL should be fixed and use the correct URL for resolution")
    void xverChoiceUrl() {
      xverChoice(u -> defs.fishForMetadata(u, Extension) != null);
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR value sets")
    void valueSets() {
      Map<String, Object> vs = md(defs, "allergyintolerance-clinical", ValueSet);
      assertEquals(allergyVsMeta(), vs);
      assertEquals(vs, md(defs, "AllergyIntoleranceClinicalStatusCodes", ValueSet));
      assertEquals(vs, md(defs, "http://hl7.org/fhir/ValueSet/allergyintolerance-clinical", ValueSet));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find base FHIR code sytems")
    void codeSystems() {
      Map<String, Object> cs = md(defs, "allergyintolerance-clinical", CodeSystem);
      assertEquals(allergyCsMeta(), cs);
      assertEquals(cs, md(defs, "AllergyIntoleranceClinicalStatusCodes", CodeSystem));
      assertEquals(cs, md(defs, "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", CodeSystem));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find other types when the fished type is Type.Instance")
    void instance() {
      Map<String, Object> sp = md(defs, "individual-gender", Instance);
      assertEquals(genderMeta(), sp);
      assertEquals(sp, md(defs, "gender", Instance));
      assertEquals(sp, md(defs, "http://hl7.org/fhir/SearchParameter/individual-gender", Instance));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find the time-traveling R5 FHIR resources when R4 is loaded")
    void timeTravelR4Resources() {
      for (String r : List.of("ActorDefinition", "Requirements", "SubscriptionTopic", "TestPlan")) {
        Map<String, Object> m = md(defs, r, Resource);
        assertEquals(exp("abstract", false, "id", r, "name", r, "sdType", r,
            "url", "http://hl7.org/fhir/StructureDefinition/" + r, "version", "5.0.0",
            "parent", "http://hl7.org/fhir/StructureDefinition/DomainResource", "resourceType", "StructureDefinition",
            "resourcePath", "virtual:sushi-r5forR4#1.0.0:" + r), m);
        assertEquals(m, md(defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find the time-traveling R5 FHIR types when R4 is loaded")
    void timeTravelR4Types() {
      for (String r : List.of("Base", "CodeableReference", "DataType")) {
        Map<String, Object> m = md(defs, r, Type);
        assertEquals(exp("abstract", !r.equals("CodeableReference"), "id", r, "name", r, "sdType", r,
            "url", "http://hl7.org/fhir/StructureDefinition/" + r, "version", "5.0.0",
            "parent", r.equals("Base") ? null : r.equals("CodeableReference")
                ? "http://hl7.org/fhir/StructureDefinition/DataType" : "http://hl7.org/fhir/StructureDefinition/Element",
            "resourceType", "StructureDefinition", "resourcePath", "virtual:sushi-r5forR4#1.0.0:" + r), m);
        assertEquals(m, md(defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find the time-traveling R5 FHIR resources when R4B is loaded")
    void timeTravelR4BResources() {
      for (String r : List.of("ActorDefinition", "Requirements", "SubscriptionTopic", "TestPlan")) {
        Map<String, Object> m = md(r4bDefs, r, Resource);
        boolean st = r.equals("SubscriptionTopic");
        assertEquals(exp("abstract", false, "id", r, "name", r, "sdType", r,
            "url", "http://hl7.org/fhir/StructureDefinition/" + r, "version", st ? "4.3.0" : "5.0.0",
            "parent", "http://hl7.org/fhir/StructureDefinition/DomainResource", "resourceType", "StructureDefinition",
            "resourcePath", st
                ? "virtual:" + r4bPkg + ":" + testDefsPath("r4b-definitions", "package",
                    "StructureDefinition-SubscriptionTopic.json")
                : "virtual:sushi-r5forR4#1.0.0:" + r), m);
        assertEquals(m, md(r4bDefs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find the time-traveling R5 FHIR types when R4B is loaded")
    void timeTravelR4BTypes() {
      for (String r : List.of("Base", "CodeableReference", "DataType")) {
        Map<String, Object> m = md(r4bDefs, r, Type);
        boolean cr = r.equals("CodeableReference");
        assertEquals(exp("abstract", !cr, "id", r, "name", r, "sdType", r,
            "url", "http://hl7.org/fhir/StructureDefinition/" + r, "version", cr ? "4.3.0" : "5.0.0",
            "parent", r.equals("Base") ? null : "http://hl7.org/fhir/StructureDefinition/Element",
            "resourceType", "StructureDefinition",
            "resourcePath", cr
                ? "virtual:" + r4bPkg + ":" + testDefsPath("r4b-definitions", "package",
                    "StructureDefinition-CodeableReference.json")
                : "virtual:sushi-r5forR4#1.0.0:" + r), m);
        assertEquals(m, md(r4bDefs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find the time-traveling R5 FHIR resources when R5 is loaded")
    void timeTravelR5Resources() {
      for (String r : List.of("ActorDefinition", "Requirements", "SubscriptionTopic", "TestPlan")) {
        Map<String, Object> m = md(r5Defs, r, Resource);
        assertEquals(exp("abstract", false, "id", r, "name", r, "sdType", r,
            "url", "http://hl7.org/fhir/StructureDefinition/" + r, "version", "5.0.0",
            "parent", "http://hl7.org/fhir/StructureDefinition/DomainResource", "resourceType", "StructureDefinition",
            "resourcePath", "virtual:" + r5Pkg + ":" + testDefsPath("r5-definitions", "package",
                "StructureDefinition-" + r + ".json")), m);
        assertEquals(m, md(r5Defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find the time-traveling R5 FHIR types when R5 is loaded")
    void timeTravelR5Types() {
      for (String r : List.of("Base", "CodeableReference", "DataType")) {
        Map<String, Object> m = md(r5Defs, r, Type);
        assertEquals(exp("abstract", !r.equals("CodeableReference"), "id", r, "name", r, "sdType", r,
            "url", "http://hl7.org/fhir/StructureDefinition/" + r, "version", "5.0.0",
            "parent", r.equals("Base") ? null : r.equals("CodeableReference")
                ? "http://hl7.org/fhir/StructureDefinition/DataType" : "http://hl7.org/fhir/StructureDefinition/Element",
            "resourceType", "StructureDefinition",
            "resourcePath", "virtual:" + r5Pkg + ":" + testDefsPath("r5-definitions", "package",
                "StructureDefinition-" + r + ".json")), m);
        assertEquals(m, md(r5Defs, "http://hl7.org/fhir/StructureDefinition/" + r));
      }
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find definitions by the enforced type order")
    void typeOrder() {
      assertEquals("http://hl7.org/fhir/ValueSet/allergyintolerance-clinical",
          defs.fishForMetadata("allergyintolerance-clinical", ValueSet, CodeSystem).url);
      assertEquals("http://hl7.org/fhir/ValueSet/allergyintolerance-clinical",
          defs.fishForMetadata("allergyintolerance-clinical", CodeSystem, ValueSet).url);
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should not find the definition when the type is not requested")
    void notRequested() {
      assertNull(defs.fishForMetadata("Condition", Logical, Type, Profile, Extension, ValueSet, CodeSystem));
      assertNull(defs.fishForMetadata("boolean", Resource, Logical, Profile, Extension, ValueSet, CodeSystem));
      assertNull(defs.fishForMetadata("Address", Resource, Logical, Profile, Extension, ValueSet, CodeSystem));
      assertNull(defs.fishForMetadata("vitalsigns", Resource, Logical, Type, Extension, ValueSet, CodeSystem));
      assertNull(defs.fishForMetadata("patient-mothersMaidenName", Resource, Logical, Type, Profile, ValueSet,
          CodeSystem));
      assertNull(defs.fishForMetadata("allergyintolerance-clinical", Resource, Logical, Type, Profile, Extension));
      assertNull(defs.fishForMetadata("w3c-provenance-activity-type", Resource, Logical, Type, Profile, Extension,
          ValueSet));
      assertNull(defs.fishForMetadata("eLTSSServiceModel", Resource, Type, Profile, Extension, ValueSet, CodeSystem));
      // (TS uses fishForFHIR for the last one)
      assertNull(defs.fishForFHIR("individual-gender", Resource, Logical, Type, Profile, Extension, ValueSet,
          CodeSystem));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should globally find any definition")
    void global() {
      assertEquals(conditionMeta(), md(defs, "Condition"));
      assertEquals(conditionMeta(), md(defs, "http://hl7.org/fhir/StructureDefinition/Condition"));
      assertEquals(booleanMeta(), md(defs, "boolean"));
      assertEquals(booleanMeta(), md(defs, "http://hl7.org/fhir/StructureDefinition/boolean"));
      assertEquals(addressMeta(), md(defs, "Address"));
      assertEquals(addressMeta(), md(defs, "http://hl7.org/fhir/StructureDefinition/Address"));
      assertEquals(vitalsMeta(), md(defs, "vitalsigns"));
      assertEquals(vitalsMeta(), md(defs, "observation-vitalsigns"));
      assertEquals(vitalsMeta(), md(defs, "http://hl7.org/fhir/StructureDefinition/vitalsigns"));
      assertEquals(maidenMeta(), md(defs, "patient-mothersMaidenName"));
      assertEquals(maidenMeta(), md(defs, "mothersMaidenName"));
      assertEquals(maidenMeta(), md(defs, "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName"));
      // When doing a non-type-specific search, we favor the ValueSet
      assertEquals(allergyVsMeta(), md(defs, "allergyintolerance-clinical"));
      assertEquals(allergyVsMeta(), md(defs, "AllergyIntoleranceClinicalStatusCodes"));
      assertEquals(allergyVsMeta(), md(defs, "http://hl7.org/fhir/ValueSet/allergyintolerance-clinical"));
      Map<String, Object> w3c = exp("id", "w3c-provenance-activity-type", "name", "W3cProvenanceActivityType",
          "url", "http://hl7.org/fhir/w3c-provenance-activity-type", "version", "4.0.1", "resourceType", "CodeSystem",
          "resourcePath", r4path("CodeSystem-w3c-provenance-activity-type.json"));
      assertEquals(w3c, md(defs, "w3c-provenance-activity-type"));
      assertEquals(w3c, md(defs, "W3cProvenanceActivityType"));
      assertEquals(w3c, md(defs, "http://hl7.org/fhir/w3c-provenance-activity-type"));
      assertEquals(eltssMeta(), md(defs, "eLTSSServiceModel"));
      assertEquals(eltssMeta(), md(defs, "ELTSSServiceModel"));
      assertEquals(eltssMeta(), md(defs, "http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel"));
      assertEquals(genderMeta(), md(defs, "individual-gender"));
      assertEquals(genderMeta(), md(defs, "gender"));
      assertEquals(genderMeta(), md(defs, "http://hl7.org/fhir/SearchParameter/individual-gender"));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadata() should find logical models with the can-bind type characteristic extension")
    void canBind() {
      Map<String, Object> m = md(defs, "BindableLM", Logical);
      assertEquals(exp("abstract", false, "id", "BindableLM", "name", "BindableLM",
          "sdType", "http://example.org/StructureDefinition/BindableLM",
          "url", "http://example.org/StructureDefinition/BindableLM",
          "parent", "http://hl7.org/fhir/StructureDefinition/Base", "resourceType", "StructureDefinition",
          "canBeTarget", false, "canBind", true,
          "resourcePath", r4path("StructureDefinition-BindableLM.json")), m);
      assertEquals(m, md(defs, "http://example.org/StructureDefinition/BindableLM", Logical));
    }
  }

  // ------------------------------------------------------------------------------------------------ #fishForMetadatas

  @Nested
  @DisplayName("#fishForMetadatas")
  class FishForMetadatas {
    @Test
    @DisplayName("FHIRDefinitions #fishForMetadatas should return all matches when there are multiple matches")
    void multiple() {
      List<Map<String, Object>> all = metas(defs.fishForMetadatas("allergyintolerance-clinical", ValueSet, CodeSystem));
      assertEquals(List.of(allergyVsMeta(), allergyCsMeta()), all);
      assertEquals(all, metas(defs.fishForMetadatas("AllergyIntoleranceClinicalStatusCodes", ValueSet, CodeSystem)));
      assertEquals(all.subList(0, 1),
          metas(defs.fishForMetadatas("http://hl7.org/fhir/ValueSet/allergyintolerance-clinical", ValueSet, CodeSystem)));
      assertEquals(all.subList(1, 2), metas(defs.fishForMetadatas(
          "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", ValueSet, CodeSystem)));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadatas should return one match when there is a single match")
    void single() {
      List<Map<String, Object>> c = metas(defs.fishForMetadatas("Condition", Resource));
      assertEquals(1, c.size());
      assertEquals(conditionMeta(), c.get(0));
      assertEquals(c, metas(defs.fishForMetadatas("http://hl7.org/fhir/StructureDefinition/Condition", Resource)));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadatas should return empty array when there are no matches")
    void none() {
      assertTrue(defs.fishForMetadatas("NonExistentThing").isEmpty());
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadatas should log message that xver extensions package is needed for cross-version extension URLs without xver package loaded")
    void xverNeeded() {
      xverNeededPackage(u -> !defs.fishForMetadatas(u, Extension).isEmpty());
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadatas should log message that cross-version extension was not found in xver package when correct xver package is loaded")
    void xverNotFound() {
      xverNotFoundInPackage(u -> !defs.fishForMetadatas(u, Extension).isEmpty());
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForMetadatas should log message that incorrect cross-version choice extension URL should be fixed and use the correct URL for resolution")
    void xverChoiceUrl() {
      xverChoice(u -> defs.fishForMetadatas(u, Extension).size() == 1);
    }
  }

  // ------------------------------------------------------------------------------------------------ predefined

  @Nested
  @DisplayName("#allPredefinedResources")
  class AllPredefinedResources {
    @Test
    @DisplayName("FHIRDefinitions #allPredefinedResources should return all predefined resources")
    @SuppressWarnings("unchecked")
    void all() {
      List<Object> results = defs.allPredefinedResources();
      assertEquals(3, results.size());
      String[][] expected = {{"StructureDefinition", "my-predefined-profile"}, {"ValueSet", "some-codes"},
          {"CodeSystem", "some-codes"}};
      for (int i = 0; i < 3; i++) {
        Map<String, Object> r = (Map<String, Object>) results.get(i);
        assertEquals(expected[i][0], r.get("resourceType"));
        assertEquals(expected[i][1], r.get("id"));
      }
    }
  }

  @Nested
  @DisplayName("#fishForPredefinedResource")
  class FishForPredefinedResource {
    @Test
    @DisplayName("FHIRDefinitions #fishForPredefinedResource should find a matching predefined resource")
    @SuppressWarnings("unchecked")
    void found() {
      Map<String, Object> result = (Map<String, Object>) defs.fishForPredefinedResource("MyPredefinedProfile");
      assertEquals("StructureDefinition", result.get("resourceType"));
      assertEquals("my-predefined-profile", result.get("id"));
      // Check snapshot just to confirm it's really the full JSON, not just metadata
      assertNotNull(result.get("snapshot"));
      assertEquals(result, defs.fishForPredefinedResource("my-predefined-profile"));
      assertEquals(result,
          defs.fishForPredefinedResource("http://example.com/StructureDefinition/my-predefined-profile"));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForPredefinedResource should return undefined when there is no match")
    void none() {
      assertNull(defs.fishForPredefinedResource("NonExistentItem"));
    }
  }

  static Map<String, Object> predefProfileMeta() {
    return exp("resourceType", "StructureDefinition", "id", "my-predefined-profile", "name", "MyPredefinedProfile",
        "url", "http://example.com/StructureDefinition/my-predefined-profile", "abstract", false,
        "resourcePath", "virtual:sushi-local#LOCAL:my-predefined-profile");
  }

  @Nested
  @DisplayName("#fishForPredefinedResourceMetadata")
  class FishForPredefinedResourceMetadata {
    @Test
    @DisplayName("FHIRDefinitions #fishForPredefinedResourceMetadata should find metadata for a matching predefined resource")
    void found() {
      Map<String, Object> result = meta(defs.fishForPredefinedResourceMetadata("MyPredefinedProfile"));
      assertEquals(predefProfileMeta(), result);
      assertEquals(result, meta(defs.fishForPredefinedResourceMetadata("my-predefined-profile")));
      assertEquals(result, meta(defs.fishForPredefinedResourceMetadata(
          "http://example.com/StructureDefinition/my-predefined-profile")));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForPredefinedResourceMetadata should return undefined when there is no match")
    void none() {
      assertNull(defs.fishForPredefinedResourceMetadata("NonExistentItem"));
    }
  }

  @Nested
  @DisplayName("#fishForPredefinedResourceMetadatas")
  class FishForPredefinedResourceMetadatas {
    @Test
    @DisplayName("FHIRDefinitions #fishForPredefinedResourceMetadatas should return all matches when there are multiple matches")
    void multiple() {
      List<Map<String, Object>> results = metas(defs.fishForPredefinedResourceMetadatas("SomeCodes"));
      assertEquals(2, results.size());
      assertEquals(List.of(
          exp("resourceType", "ValueSet", "id", "some-codes", "name", "SomeCodes",
              "url", "http://example.com/ValueSet/some-codes", "resourcePath", "virtual:sushi-local#LOCAL:some-codes-vs"),
          exp("resourceType", "CodeSystem", "id", "some-codes", "name", "SomeCodes",
              "url", "http://example.com/CodeSystem/some-codes",
              "resourcePath", "virtual:sushi-local#LOCAL:some-codes-cs")), results);
      assertEquals(results, metas(defs.fishForPredefinedResourceMetadatas("some-codes")));
      assertEquals(results.subList(0, 1),
          metas(defs.fishForPredefinedResourceMetadatas("http://example.com/ValueSet/some-codes")));
      assertEquals(results.subList(1, 2),
          metas(defs.fishForPredefinedResourceMetadatas("http://example.com/CodeSystem/some-codes")));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForPredefinedResourceMetadatas should return one match when there is a single match")
    void single() {
      List<Map<String, Object>> results = metas(defs.fishForPredefinedResourceMetadatas("MyPredefinedProfile"));
      assertEquals(1, results.size());
      assertEquals(predefProfileMeta(), results.get(0));
      assertEquals(results, metas(defs.fishForPredefinedResourceMetadatas("my-predefined-profile")));
      assertEquals(results, metas(defs.fishForPredefinedResourceMetadatas(
          "http://example.com/StructureDefinition/my-predefined-profile")));
    }

    @Test
    @DisplayName("FHIRDefinitions #fishForPredefinedResourceMetadatas should return empty array when there is no match")
    void none() {
      assertTrue(defs.fishForPredefinedResourceMetadatas("NonExistentItem").isEmpty());
    }
  }
}
