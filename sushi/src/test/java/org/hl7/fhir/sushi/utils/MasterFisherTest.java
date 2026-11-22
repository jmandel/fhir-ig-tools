package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.MetadataAssert.*;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

// port of sushi/test/utils/MasterFisher.test.ts (SUSHI 3.21.1)
// TS beforeAll state (one fisher, one defs) is shared by all tests in declaration order: PER_CLASS + method-name order.
@DisplayName("MasterFisher")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class MasterFisherTest {
  MasterFisher fisher;
  TestFHIRDefinitions defs;

  @BeforeAll
  void setUpAll() {
    Assumptions.assumeTrue(TestFHIRDefinitions.tsFixturesAvailable(), "TS SUSHI checkout not found");
    FSHDocument doc1 = new FSHDocument("doc.fsh");
    doc1.aliases.put("TankProfile1", "http://hl7.org/fhir/us/minimal/StructureDefinition/prf1");
    doc1.aliases.put("PkgProfile3", "http://hl7.org/fhir/us/minimal/StructureDefinition/profile3");
    doc1.aliases.put("FHIRPatient", "http://hl7.org/fhir/StructureDefinition/Patient");
    doc1.profiles.put("Profile1", new Profile("Profile1"));
    doc1.profiles.get("Profile1").setId("prf1");
    doc1.profiles.get("Profile1").parent = "Procedure";
    doc1.profiles.put("Profile2", new Profile("Profile2"));
    doc1.profiles.get("Profile2").parent = "bp";
    doc1.profiles.put("Organization", new Profile("Organization"));
    doc1.profiles.get("Organization").setId("my-org");
    doc1.profiles.get("Organization").parent = "http://hl7.org/fhir/StructureDefinition/Organization";
    doc1.profiles.put("Practitioner",
        new Profile("Practitioner").<Profile>withFile("Practitioner.fsh").withLocation(2, 8, 4, 28));
    doc1.profiles.get("Practitioner").setId("my-dr");
    doc1.profiles.get("Practitioner").parent = "Practitioner";
    doc1.instances.put("Instance1", new Instance("Instance1"));
    doc1.instances.get("Instance1").setId("inst1");
    doc1.instances.get("Instance1").instanceOf = "Profile1";
    doc1.instances.put("InlineInstance", new Instance("InlineInstance"));
    doc1.instances.get("InlineInstance").setId("inline-instance");
    doc1.instances.get("InlineInstance").instanceOf = "Profile1";
    doc1.instances.get("InlineInstance").usage = InstanceUsage.Inline;
    FSHTank tank = new FSHTank(new ArrayList<>(List.of(doc1)), minimalConfig());

    Package pkg = new Package(tank.config);
    StructureDefinition profile3 = new StructureDefinition();
    profile3.setName("Profile3");
    profile3.setId("profile3");
    profile3.setType("Condition");
    profile3.setUrl("http://hl7.org/fhir/us/minimal/StructureDefinition/profile3");
    profile3.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Condition");
    profile3.setFhirVersion("4.0.1");
    StructureDefinition profile4 = new StructureDefinition();
    profile4.setName("MyVitalSigns");
    profile4.setId("vitalsigns");
    profile4.setType("Observation");
    profile4.setUrl("http://hl7.org/fhir/us/minimal/StructureDefinition/vitalsigns");
    profile4.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Observation");
    profile4.setFhirVersion("4.0.1");
    pkg.profiles.add(profile3);
    pkg.profiles.add(profile4);

    defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));

    fisher = new MasterFisher(tank, defs, pkg);
  }

  @BeforeEach
  void setUp() {
    loggerSpy.reset();
  }

  static String r4Path(String file) {
    return testDefsPath("r4-definitions", "package", file);
  }

  @Test
  @DisplayName("MasterFisher should correctly determine its FHIR version from a loaded StructureDefinition")
  void t01() {
    assertEquals("4.0.1", fisher.defaultFHIRVersion);
  }

  @Test
  @DisplayName("MasterFisher should fallback to the config when it cannot determine its FHIR version from a loaded StructureDefinition")
  void t02() {
    Configuration r4bConfig = minimalConfig();
    r4bConfig.fhirVersion = new ArrayList<>(List.of("4.3.0"));
    FSHTank r4bTank = new FSHTank(new ArrayList<>(List.of(new FSHDocument("doc.fsh"))), r4bConfig);
    FHIRDefinitions defs = getTestFHIRDefinitions();
    MasterFisher r4bFisher = new MasterFisher(r4bTank, defs, new Package(r4bTank.config));
    assertEquals("4.3.0", r4bFisher.defaultFHIRVersion);
  }

  @Test
  @DisplayName("MasterFisher should find a profile that is only in the tank")
  void t03() {
    Object result = fisher.fishForFHIR("Profile1");
    assertNull(result); // NOTE: It is only in the tank and the tank does not support FHIR

    Metadata expectedMD = md("id", "prf1", "name", "Profile1", "version", "1.0.0", "sdType", "Procedure", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/prf1", "parent", "Procedure", "resourceType",
        "StructureDefinition");

    assertMetadataEquals(expectedMD, fisher.fishForMetadata("Profile1"));
    assertMetadatasEqual(List.of(expectedMD), fisher.fishForMetadatas("Profile1"));
  }

  @Test
  @DisplayName("MasterFisher should find the correct sdType for a profile of a profile that is only in the tank")
  void t04() {
    Object result = fisher.fishForFHIR("Profile2");
    assertNull(result); // NOTE: It is only in the tank and the tank does not support FHIR

    Metadata expectedMD = md("id", "Profile2", "name", "Profile2", "version", "1.0.0", "sdType", "Observation", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/Profile2", "parent", "bp", "resourceType",
        "StructureDefinition");

    assertMetadataEquals(expectedMD, fisher.fishForMetadata("Profile2"));
    assertMetadatasEqual(List.of(expectedMD), fisher.fishForMetadatas("Profile2"));
  }

  @Test
  @DisplayName("MasterFisher should log an error when encountering circular dependencies when determining sdType")
  void t05() {
    Object result = fisher.fishForFHIR("my-dr");
    assertNull(result); // NOTE: It is only in the tank and the tank does not support FHIR

    Metadata expectedMD = md("id", "my-dr", "name", "Practitioner", "version", "1.0.0", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/my-dr", "parent", "Practitioner", "resourceType",
        "StructureDefinition");

    // This next line causes the fisher to have to look up the parent chain to find the sdType. This will cause an
    // error because Practitioner basically declares itself as its own parent.
    Metadata resultMD = fisher.fishForMetadata("my-dr");
    assertMatches("Circular dependency .* Practitioner < Practitioner", loggerSpy.getLastMessage());
    assertMatches("File: Practitioner\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage());
    assertMetadataEquals(expectedMD, resultMD);

    loggerSpy.reset();
    List<Metadata> resultMDs = fisher.fishForMetadatas("my-dr");
    assertMatches("Circular dependency .* Practitioner < Practitioner", loggerSpy.getLastMessage());
    assertMatches("File: Practitioner\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage());
    assertMetadatasEqual(List.of(expectedMD), resultMDs);
  }

  @Test
  @DisplayName("MasterFisher should find a profile that is only in the package (not likely, but good to test)")
  void t06() {
    Object result = fisher.fishForFHIR("Profile3");
    assertEquals("profile3", at(result, "id"));
    assertEquals("4.0.1", at(result, "fhirVersion"));

    Metadata expectedMD = md("id", "profile3", "name", "Profile3", "sdType", "Condition", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/profile3", "parent",
        "http://hl7.org/fhir/StructureDefinition/Condition", "resourceType", "StructureDefinition");

    assertMetadataEquals(expectedMD, fisher.fishForMetadata("Profile3"));
    assertMetadatasEqual(List.of(expectedMD), fisher.fishForMetadatas("Profile3"));
  }

  @Test
  @DisplayName("MasterFisher should find a resource that is only in the FHIR definitions")
  void t07() {
    Object result = fisher.fishForFHIR("Patient");
    assertEquals("Patient", at(result, "id"));
    assertEquals("4.0.1", at(result, "fhirVersion"));

    Metadata expectedMD = md("_abstract", false, "id", "Patient", "name", "Patient", "sdType", "Patient", "url",
        "http://hl7.org/fhir/StructureDefinition/Patient", "version", "4.0.1", "parent",
        "http://hl7.org/fhir/StructureDefinition/DomainResource", "resourceType", "StructureDefinition",
        "resourcePath", "virtual:hl7.fhir.r4.core#4.0.1:" + r4Path("StructureDefinition-Patient.json"));

    assertMetadataEquals(expectedMD, fisher.fishForMetadata("Patient"));
    assertMetadatasEqual(List.of(expectedMD), fisher.fishForMetadatas("Patient"));
  }

  @Test
  @DisplayName("MasterFisher should return the profile from the package first when it exists in the package and FHIR definitions")
  void t08() {
    // fishForFHIR only returns the first match (the one from the package)
    Object result = fisher.fishForFHIR("vitalsigns");
    assertEquals("MyVitalSigns", at(result, "name"));
    assertEquals("4.0.1", at(result, "fhirVersion"));

    Metadata expectedPackageMD = md("id", "vitalsigns", "name", "MyVitalSigns", "sdType", "Observation", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/vitalsigns", "parent",
        "http://hl7.org/fhir/StructureDefinition/Observation", "resourceType", "StructureDefinition");
    Metadata expectedFHIRMD = md("id", "vitalsigns", "name", "observation-vitalsigns", "sdType", "Observation",
        "url", "http://hl7.org/fhir/StructureDefinition/vitalsigns", "parent",
        "http://hl7.org/fhir/StructureDefinition/Observation", "_abstract", false, "version", "4.0.1",
        "resourceType", "StructureDefinition", "resourcePath",
        "virtual:hl7.fhir.r4.core#4.0.1:" + r4Path("StructureDefinition-vitalsigns.json"));

    // fishForMetada only returns the first match (the one from the package)
    assertMetadataEquals(expectedPackageMD, fisher.fishForMetadata("vitalsigns"));
    // fishForMetadas returns all matches
    assertMetadatasEqual(List.of(expectedPackageMD, expectedFHIRMD), fisher.fishForMetadatas("vitalsigns"));
  }

  @Test
  @DisplayName("MasterFisher should return a profile that is predefined first when it also exists in the package")
  void t09() {
    String fhirVitalSignsPath = r4Path("StructureDefinition-vitalsigns.json");

    // Mark vital signs as predefined
    defs.loadCustomResources(fhirVitalSignsPath);

    Metadata expectedPredefinedMD = md("id", "vitalsigns", "name", "observation-vitalsigns", "sdType",
        "Observation", "url", "http://hl7.org/fhir/StructureDefinition/vitalsigns", "parent",
        "http://hl7.org/fhir/StructureDefinition/Observation", "_abstract", false, "version", "4.0.1",
        "resourceType", "StructureDefinition", "resourcePath", "virtual:" + FHIRDefinitions.PREDEFINED_PACKAGE_NAME
            + "#" + FHIRDefinitions.PREDEFINED_PACKAGE_VERSION + ":" + fhirVitalSignsPath);
    Metadata expectedPackageMD = md("id", "vitalsigns", "name", "MyVitalSigns", "sdType", "Observation", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/vitalsigns", "parent",
        "http://hl7.org/fhir/StructureDefinition/Observation", "resourceType", "StructureDefinition");
    Metadata expectedFHIRMD = md("id", "vitalsigns", "name", "observation-vitalsigns", "sdType", "Observation",
        "url", "http://hl7.org/fhir/StructureDefinition/vitalsigns", "parent",
        "http://hl7.org/fhir/StructureDefinition/Observation", "_abstract", false, "version", "4.0.1",
        "resourceType", "StructureDefinition", "resourcePath",
        "virtual:hl7.fhir.r4.core#4.0.1:" + fhirVitalSignsPath);

    // fishForFHIR only returns the first match (the predefined one)
    Object result = fisher.fishForFHIR("vitalsigns");
    assertEquals("observation-vitalsigns", at(result, "name"));

    // fishForMetadata only returns the first match (the predefined one)
    assertMetadataEquals(expectedPredefinedMD, fisher.fishForMetadata("vitalsigns"));

    // fishForMetadas returns all matches
    assertMetadatasEqual(List.of(expectedPredefinedMD, expectedPackageMD, expectedFHIRMD),
        fisher.fishForMetadatas("vitalsigns"));
  }

  @Test
  @DisplayName("MasterFisher should find an Instance that is only in the Tank")
  void t10() {
    assertNull(fisher.fishForFHIR("Instance1"));

    Metadata expectedMD = md("id", "inst1", "name", "Instance1", "version", "1.0.0", "instanceUsage", "Example",
        "resourceType", "Procedure", "url", "http://hl7.org/fhir/us/minimal/Procedure/inst1");

    assertMetadataEquals(expectedMD, fisher.fishForMetadata("Instance1"));
    assertMetadatasEqual(List.of(expectedMD), fisher.fishForMetadatas("Instance1"));
  }

  @Test
  @DisplayName("MasterFisher should find an inline Instance that is only in the Tank")
  void t11() {
    assertNull(fisher.fishForFHIR("InlineInstance"));

    Metadata expectedMD = md("id", "inline-instance", "name", "InlineInstance", "version", "1.0.0",
        "instanceUsage", "Inline", "resourceType", "Procedure");

    assertMetadataEquals(expectedMD, fisher.fishForMetadata("InlineInstance"));
    assertMetadatasEqual(List.of(expectedMD), fisher.fishForMetadatas("InlineInstance"));
  }

  @Test
  @DisplayName("MasterFisher should not return the FHIR def for a resource if there is a profile w/ the same name in the tank")
  void t12() {
    assertNull(fisher.fishForFHIR("Organization"));

    Metadata expectedProfileMD = md("id", "my-org", "name", "Organization", "version", "1.0.0", "sdType",
        "Organization", "url", "http://hl7.org/fhir/us/minimal/StructureDefinition/my-org", "parent",
        "http://hl7.org/fhir/StructureDefinition/Organization", "resourceType", "StructureDefinition");
    Metadata expectedFHIRMD = md("id", "Organization", "name", "Organization", "sdType", "Organization", "url",
        "http://hl7.org/fhir/StructureDefinition/Organization", "parent",
        "http://hl7.org/fhir/StructureDefinition/DomainResource", "_abstract", false, "version", "4.0.1",
        "resourceType", "StructureDefinition", "resourcePath",
        "virtual:hl7.fhir.r4.core#4.0.1:" + r4Path("StructureDefinition-Organization.json"));

    assertMetadataEquals(expectedProfileMD, fisher.fishForMetadata("Organization"));
    assertMetadatasEqual(List.of(expectedProfileMD, expectedFHIRMD), fisher.fishForMetadatas("Organization"));
  }

  @Test
  @DisplayName("MasterFisher should support fishing using aliases when fishing the Tank")
  void t13() {
    assertNull(fisher.fishForFHIR("TankProfile1")); // the tank doesn't return FHIR

    assertEquals("prf1", fisher.fishForMetadata("TankProfile1").id);

    List<Metadata> resultMDs = fisher.fishForMetadatas("TankProfile1");
    assertEquals(1, resultMDs.size());
    assertEquals("prf1", resultMDs.get(0).id);
  }

  @Test
  @DisplayName("MasterFisher should support fishing using aliases when fishing the Pkg")
  void t14() {
    assertEquals("profile3", at(fisher.fishForFHIR("PkgProfile3"), "id"));

    assertEquals("profile3", fisher.fishForMetadata("PkgProfile3").id);

    List<Metadata> resultMDs = fisher.fishForMetadatas("PkgProfile3");
    assertEquals(1, resultMDs.size());
    assertEquals("profile3", resultMDs.get(0).id);
  }

  @Test
  @DisplayName("MasterFisher should support fishing using aliases when fishing the FHIR definitions")
  void t15() {
    assertEquals("Patient", at(fisher.fishForFHIR("FHIRPatient"), "id"));

    assertEquals("Patient", fisher.fishForMetadata("FHIRPatient").id);

    List<Metadata> resultMDs = fisher.fishForMetadatas("FHIRPatient");
    assertEquals(1, resultMDs.size());
    assertEquals("Patient", resultMDs.get(0).id);
  }

  @Nested
  @DisplayName("type caching")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class TypeCaching {
    /** jest.spyOn(defs, 'fishForFHIR'): a defs whose fishForFHIR calls are counted. */
    final class CountingDefs extends TestFHIRDefinitions {
      int calls;

      @java.lang.Override public Object fishForFHIR(String item, org.hl7.fhir.sushi.utils.FishableType... types) {
        calls++;
        return super.fishForFHIR(item, types);
      }
    }

    CountingDefs countingDefs;
    MasterFisher typeFisher;

    @BeforeAll
    void setUpCounting() {
      countingDefs = new CountingDefs();
      countingDefs.loadLocalPaths(testDefsPath("r4-definitions"));
    }

    @BeforeEach
    void setUpTypeFisher() {
      // the MasterFisher constructor fishes for StructureDefinition, so clear the spy after it
      typeFisher = new MasterFisher(null, countingDefs, null);
      countingDefs.calls = 0;
    }

    @Test
    @DisplayName("MasterFisher type caching should only look up a type once when fishing for it repeatedly with only Type.Type")
    void t1() {
      Object first = typeFisher.fishForFHIR("string", FishableType.Type);
      Object second = typeFisher.fishForFHIR("string", FishableType.Type);
      assertEquals("string", at(first, "id"));
      assertSame(first, second);
      assertEquals(1, countingDefs.calls);
    }

    @Test
    @DisplayName("MasterFisher type caching should not cache results when fishing for types other than Type.Type")
    void t2() {
      typeFisher.fishForFHIR("Patient", FishableType.Resource);
      typeFisher.fishForFHIR("Patient", FishableType.Resource);
      assertEquals(2, countingDefs.calls);
    }
  }

  @Test
  @DisplayName("MasterFisher should return undefined or empty list when fishing for something that is not in any locations")
  void t16() {
    assertNull(fisher.fishForFHIR("Foo"));
    assertNull(fisher.fishForMetadata("Foo"));
    assertTrue(fisher.fishForMetadatas("Foo").isEmpty());
  }
}
