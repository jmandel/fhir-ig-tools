package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.export.StructureDefinitionExporter;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.*;

// port of sushi/test/fhirtypes/ElementDefinition.constrainType.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionConstrainTypeTest {
  static CaretValueRule imposeProfileRule(Object imposeProfileURL) {
    CaretValueRule rule = new CaretValueRule("");
    rule.caretPath =
        "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile].valueCanonical";
    rule.value = imposeProfileURL;
    return rule;
  }

  static FHIRDefinitions defs;
  StructureDefinition observation;
  static Map<String, Object> jsonModifiedObservation;
  StructureDefinition modifiedObservation;
  StructureDefinition planDefinition;
  StructureDefinition extension;
  static TestFisher fisher;
  static StructureDefinitionExporter exporter;
  static Package pkg;

  @SuppressWarnings("unchecked")
  static Map<String, Object> snapEl(Map<String, Object> sd, String id) {
    for (Object e : (List<Object>) at(sd, "snapshot.element")) if (id.equals(at(e, "id"))) return (Map<String, Object>) e;
    return null;
  }

  @SuppressWarnings("unchecked")
  @BeforeAll
  static void beforeAll0() {
    // fresh (not the shared cached) definitions: a test below loads extra local paths into them
    defs = org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions(true,
        org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath("r4-definitions"));
    pkg = new Package(minimalConfig());
    fisher = new TestFisher().withFHIR(defs).withPackage(pkg);
    jsonModifiedObservation = (Map<String, Object>) Js.cloneDeep(defs.fishForFHIR("Observation", FishableType.Resource));
    java.util.function.BiFunction<String, Boolean, Object> createExtra = (id, ms) -> obj("id", id, "extension",
        list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", ms)));
    var identifier = snapEl(jsonModifiedObservation, "Observation.identifier");
    setAt(identifier, "type[0].profile", list("http://example.org/identifiers/StructureDefinition/required-type-identifier", "http://example.org/identifiers/StructureDefinition/required-use-identifier", "http://example.org/identifiers/StructureDefinition/required-value-identifier"));
    setAt(identifier, "type[0]._profile", list(createExtra.apply("type-extra", true), null, createExtra.apply("value-extra", true)));
    var focus = snapEl(jsonModifiedObservation, "Observation.focus");
    setAt(focus, "type[0]._targetProfile", list(createExtra.apply("any-extra", true)));
    var hasMember = snapEl(jsonModifiedObservation, "Observation.hasMember");
    setAt(hasMember, "type[0]._targetProfile", list(createExtra.apply("observation-extra", true), createExtra.apply("questionnaire-response-extra", true), null));
    var subject = snapEl(jsonModifiedObservation, "Observation.subject");
    setAt(subject, "type[0]._targetProfile", list(createExtra.apply("patient-extra", true), createExtra.apply("group-extra", true), null, null));
    var performer = snapEl(jsonModifiedObservation, "Observation.performer");
    setAt(performer, "type[0]._targetProfile", list(createExtra.apply("practitioner-extra", true), null, createExtra.apply("organization-extra", false), createExtra.apply("care-team-extra", false), null, createExtra.apply("related-person-extra", true)));
    exporter = new StructureDefinitionExporter(new FSHTank(new ArrayList<>(), minimalConfig()), pkg, fisher);
  }
  @BeforeEach
  void beforeEach1() {
    loggerSpy.reset();
    pkg.clearAllDefinitions();
    // Clear packages so definitions from one test don't affect another test
    observation = fisher.fishForStructureDefinition("Observation");
    modifiedObservation = StructureDefinition.fromJSON(jsonModifiedObservation);
    planDefinition = fisher.fishForStructureDefinition("PlanDefinition");
    extension = fisher.fishForStructureDefinition("Extension");
  }

  @Nested
  @DisplayName("#constrainType()")
  class ConstrainType {

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a choice to be constrained to a subset")
    void t2() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("Quantity", null, null, null), ort("integer", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(2, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity"), at(valueX, "type[0]"));
      assertJsEqual(new ElementDefinitionType("integer"), at(valueX, "type[1]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should maintain original type order when constraining to a subset")
    void t3() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("Period", null, null, null), ort("integer", null, null, null), ort("Quantity", null, null, null), ort("Ratio", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(4, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity"), at(valueX, "type[0]"));
      assertJsEqual(new ElementDefinitionType("integer"), at(valueX, "type[1]"));
      assertJsEqual(new ElementDefinitionType("Ratio"), at(valueX, "type[2]"));
      assertJsEqual(new ElementDefinitionType("Period"), at(valueX, "type[3]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a choice to be constrained to a single item")
    void t4() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("Quantity", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a choice to be constrained to a single item by its URL")
    void t5() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/Quantity", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a choice to be constrained to a single profile")
    void t6() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("SimpleQuantity", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity").withProfiles("http://hl7.org/fhir/StructureDefinition/SimpleQuantity"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a choice to be constrained to a profile of Reference")
    void t7() {
      ((org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions) defs).loadLocalPaths(org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath("StructureDefinition-reference-with-type.json"));
      ElementDefinition valueX = el(extension, "Extension.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("ReferenceWithType", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withProfiles("http://example.org/StructureDefinition/reference-with-type"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type to be constrained to multiple profiles")
    void t8() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("SimpleQuantity", null, null, null), ort("MoneyQuantity", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity").withProfiles("http://hl7.org/fhir/StructureDefinition/SimpleQuantity", "http://hl7.org/fhir/StructureDefinition/MoneyQuantity"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type to be constrained to multiple profiles and itself")
    void t9() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("SimpleQuantity", null, null, null), ort("MoneyQuantity", null, null, null), ort("Quantity", null, null, null)));
      valueX.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity").withProfiles("http://hl7.org/fhir/StructureDefinition/SimpleQuantity", "http://hl7.org/fhir/StructureDefinition/MoneyQuantity", "http://hl7.org/fhir/StructureDefinition/Quantity"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow Resource to be constrained to a resource")
    void t10() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      var resourceConstraint = new OnlyRule("entry.resource");
      resourceConstraint.types = new ArrayList<>(List.of(ort("Patient", null, null, null)));
      entryResource.constrainType(resourceConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Patient"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow Resource to be constrained to a profile")
    void t11() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      var resourceConstraint = new OnlyRule("entry.resource");
      resourceConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bp", null, null, null)));
      entryResource.constrainType(resourceConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a profile to be constrained to a more specific profile")
    void t12() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile = new Profile("Foo");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/bp";
      exporter.exportStructDef(profile);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("Foo", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should constrain a type to a profile whose id is the same as a FHIR resource")
    void t13() {
      // regression test for https://github.com/FHIR/sushi/issues/1271 -- see #1560
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      // the id of this profile is deliberately the same as the resource it profiles
      var profile = new Profile("MyObservation");
      profile.setId("Observation");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/Observation";
      exporter.exportStructDef(profile);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("MyObservation", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/Observation"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should constrain a type to a profile whose id is the same as a FHIR resource by url")
    void t14() {
      // the issue reports that naming the profile by its canonical url does not help either
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      var profile = new Profile("MyObservation");
      profile.setId("Observation");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/Observation";
      exporter.exportStructDef(profile);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/us/minimal/StructureDefinition/Observation", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/Observation"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should not record a profile when a type is constrained to itself")
    void t15() {
      // guards the narrowed condition: constraining to the type itself must stay profile-free
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      var resourceConstraint = new OnlyRule("entry.resource");
      resourceConstraint.types = new ArrayList<>(List.of(ort("Observation", null, null, null)));
      entryResource.constrainType(resourceConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a version-pinned profile to be constrained to a more specific profile")
    void t16() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp|4.0.1"));
      var profile = new Profile("Foo");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/bp";
      exporter.exportStructDef(profile);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("Foo", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a profile to be constrained to a profile that declares it in an imposeProfile extension")
    void t17() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      profile.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("SomeOtherObsProfile", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a profile to be constrained to a profile that declares it in an imposeProfile extension using FshCanonical")
    void t18() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      profile.rules.add(imposeProfileRule(new FshCanonical("bp")));
      exporter.exportStructDef(profile);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("SomeOtherObsProfile", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a profile is constrained to a profile that does not formally extend from it nor declare it in an imposeProfile extension")
    void t19() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      exporter.exportStructDef(profile);
      assertThrowsMatching("\"SomeOtherObsProfile\" does not match .* http:\\/\\/hl7\\.org\\/fhir\\/StructureDefinition\\/bp", () -> { var profileConstraint = new OnlyRule("entry.resource");
profileConstraint.types = new ArrayList<>(List.of(ort("SomeOtherObsProfile", null, null, null)));
entryResource.constrainType(profileConstraint, fisher);
 });
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a profile to be constrained to multiple profiles that declare it in imposeProfile extensions")
    void t20() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile1 = new Profile("AnObsProfile");
      profile1.parent = "Observation";
      profile1.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile1);
      var profile2 = new Profile("SomeOtherObsProfile");
      profile2.parent = "Observation";
      profile2.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile2);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("AnObsProfile", null, null, null), ort("SomeOtherObsProfile", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/AnObsProfile", "http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a profile to be constrained to multiple profiles that declare it in imposeProfile extensions with and without FshCanonical")
    void t21() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile1 = new Profile("AnObsProfile");
      profile1.parent = "Observation";
      profile1.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile1);
      var profile2 = new Profile("SomeOtherObsProfile");
      profile2.parent = "Observation";
      profile2.rules.add(imposeProfileRule(new FshCanonical("bp")));
      exporter.exportStructDef(profile2);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("AnObsProfile", null, null, null), ort("SomeOtherObsProfile", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/AnObsProfile", "http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a profile is constrained to multiple profiles that do not formally extend from it nor declare it in imposeProfile extensions")
    void t22() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile1 = new Profile("AnObsProfile");
      profile1.parent = "Observation";
      exporter.exportStructDef(profile1);
      var profile2 = new Profile("SomeOtherObsProfile");
      profile2.parent = "Observation";
      exporter.exportStructDef(profile2);
      assertThrowsMatching("\"AnObsProfile\" does not match .* http:\\/\\/hl7\\.org\\/fhir\\/StructureDefinition\\/bp", () -> { var profileConstraint = new OnlyRule("entry.resource");
profileConstraint.types = new ArrayList<>(List.of(ort("AnObsProfile", null, null, null), ort("SomeOtherObsProfile", null, null, null)));
entryResource.constrainType(profileConstraint, fisher);
 });
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a profile to be constrained to multiple profiles when one formally extends from it and the other declares it in an imposeProfile extension")
    void t23() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile1 = new Profile("ProfileOfBP");
      profile1.parent = "http://hl7.org/fhir/StructureDefinition/bp";
      exporter.exportStructDef(profile1);
      var profile2 = new Profile("SomeOtherObsProfile");
      profile2.parent = "Observation";
      profile2.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile2);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("ProfileOfBP", null, null, null), ort("SomeOtherObsProfile", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileOfBP", "http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a profile is constrained to a profile that formally extends from it and another profile that does not extend it nor declare it in an imposeProfile extension")
    void t24() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile1 = new Profile("ProfileOfBP");
      profile1.parent = "http://hl7.org/fhir/StructureDefinition/bp";
      exporter.exportStructDef(profile1);
      var profile2 = new Profile("SomeOtherObsProfile");
      profile2.parent = "Observation";
      exporter.exportStructDef(profile2);
      assertThrowsMatching("\"SomeOtherObsProfile\" does not match .* http:\\/\\/hl7\\.org\\/fhir\\/StructureDefinition\\/bp", () -> { var profileConstraint = new OnlyRule("entry.resource");
profileConstraint.types = new ArrayList<>(List.of(ort("ProfileOfBP", null, null, null), ort("SomeOtherObsProfile", null, null, null)));
entryResource.constrainType(profileConstraint, fisher);
 });
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should remove a _profile entry when a set of profiles is constrained to a subset of profiles")
    void t25() {
      ElementDefinition identifier = el(modifiedObservation, "Observation.identifier");
      var identifierConstraint = new OnlyRule("entry.resource");
      identifierConstraint.types = new ArrayList<>(List.of(ort("required-use-identifier", null, null, null), ort("required-value-identifier", null, null, null)));
      identifier.constrainType(identifierConstraint, fisher);
      var expectedType = new ElementDefinitionType("Identifier").withProfiles("http://example.org/identifiers/StructureDefinition/required-use-identifier", "http://example.org/identifiers/StructureDefinition/required-value-identifier");
      setAt(expectedType, "_profile", list(null, obj("id", "value-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true)))));
      assertEquals(1, size(at(identifier, "type")));
      assertJsEqual(expectedType, at(identifier, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should null a _profile entry when a profile is constrained to a more specific profile")
    void t26() {
      ElementDefinition identifier = el(modifiedObservation, "Observation.identifier");
      var identifierConstraint = new OnlyRule("entry.resource");
      identifierConstraint.types = new ArrayList<>(List.of(ort("required-type-identifier", null, null, null), ort("required-use-identifier", null, null, null), ort("required-system-value-identifier", null, null, null)));
      identifier.constrainType(identifierConstraint, fisher);
      var expectedType = new ElementDefinitionType("Identifier").withProfiles("http://example.org/identifiers/StructureDefinition/required-type-identifier", "http://example.org/identifiers/StructureDefinition/required-use-identifier", "http://example.org/identifiers/StructureDefinition/required-system-value-identifier");
      setAt(expectedType, "_profile", list(obj("id", "type-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true))), null, null));
      assertEquals(1, size(at(identifier, "type")));
      assertJsEqual(expectedType, at(identifier, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should null multiple _profile entries when a profile is constrained to multiple more specific profiles")
    void t27() {
      ElementDefinition identifier = el(modifiedObservation, "Observation.identifier");
      var identifierConstraint = new OnlyRule("entry.resource");
      identifierConstraint.types = new ArrayList<>(List.of(ort("required-type-identifier", null, null, null), ort("required-use-identifier", null, null, null), ort("required-system-value-identifier", null, null, null), ort("required-type-value-identifier", null, null, null)));
      identifier.constrainType(identifierConstraint, fisher);
      var expectedType = new ElementDefinitionType("Identifier").withProfiles("http://example.org/identifiers/StructureDefinition/required-type-identifier", "http://example.org/identifiers/StructureDefinition/required-use-identifier", "http://example.org/identifiers/StructureDefinition/required-system-value-identifier", "http://example.org/identifiers/StructureDefinition/required-type-value-identifier");
      setAt(expectedType, "_profile", list(obj("id", "type-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true))), null, null, null));
      assertEquals(1, size(at(identifier, "type")));
      assertJsEqual(expectedType, at(identifier, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should remove _profile array when only null values are left")
    void t28() {
      ElementDefinition identifier = el(modifiedObservation, "Observation.identifier");
      var identifierConstraint = new OnlyRule("entry.resource");
      identifierConstraint.types = new ArrayList<>(List.of(ort("required-use-identifier", null, null, null)));
      identifier.constrainType(identifierConstraint, fisher);
      assertEquals(1, size(at(identifier, "type")));
      assertJsEqual(new ElementDefinitionType("Identifier").withProfiles("http://example.org/identifiers/StructureDefinition/required-use-identifier"), at(identifier, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a profile to be constrained to a more specific profile of a child type")
    void t29() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      setAt(entryResource, "type[0]", new ElementDefinitionType("Resource").withProfiles("http://hl7.org/fhir/StructureDefinition/bp"));
      var profile = new Profile("Foo");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/bp";
      exporter.exportStructDef(profile);
      var profileConstraint = new OnlyRule("entry.resource");
      profileConstraint.types = new ArrayList<>(List.of(ort("Foo", null, null, null)));
      entryResource.constrainType(profileConstraint, fisher);
      assertEquals(1, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo"), at(entryResource, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow Resource to be constrained to multiple resources and profiles")
    void t30() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      var resourceConstraint = new OnlyRule("entry.resource");
      resourceConstraint.types = new ArrayList<>(List.of(ort("Practitioner", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyheight", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/familymemberhistory-genetic", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/Procedure", null, null, null)));
      entryResource.constrainType(resourceConstraint, fisher);
      assertEquals(4, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Practitioner"), at(entryResource, "type[0]"));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(entryResource, "type[1]"));
      assertJsEqual(new ElementDefinitionType("FamilyMemberHistory").withProfiles("http://hl7.org/fhir/StructureDefinition/familymemberhistory-genetic"), at(entryResource, "type[2]"));
      assertJsEqual(new ElementDefinitionType("Procedure"), at(entryResource, "type[3]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a choice to be constrained such that only the target type is constrained to a profile and others remain as-is")
    void t31() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("SimpleQuantity", null, null, null)));
      valueX.constrainType(valueConstraint, fisher, "Quantity");
      assertEquals(11, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity").withProfiles("http://hl7.org/fhir/StructureDefinition/SimpleQuantity"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @Disabled("skipped in TS")
    @DisplayName("ElementDefinition #constrainType() should allow a choice with profiles to be constrained such that only the target profile is constrained and others remain as-is")
    void t32() {
      // Cannot find any examples to use for testing.  Will revisit when we can reference FSH profiles
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow Resource to be constrained to multiple resources and profiles when it is specifically targeted")
    void t33() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      var resourceConstraint = new OnlyRule("entry.resource");
      resourceConstraint.types = new ArrayList<>(List.of(ort("Practitioner", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyheight", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/familymemberhistory-genetic", null, null, null), ort("http://hl7.org/fhir/StructureDefinition/Procedure", null, null, null)));
      entryResource.constrainType(resourceConstraint, fisher, "Resource");
      assertEquals(4, size(at(entryResource, "type")));
      assertJsEqual(new ElementDefinitionType("Practitioner"), at(entryResource, "type[0]"));
      assertJsEqual(new ElementDefinitionType("Observation").withProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(entryResource, "type[1]"));
      assertJsEqual(new ElementDefinitionType("FamilyMemberHistory").withProfiles("http://hl7.org/fhir/StructureDefinition/familymemberhistory-genetic"), at(entryResource, "type[2]"));
      assertJsEqual(new ElementDefinitionType("Procedure"), at(entryResource, "type[3]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to Resource to be constrained to a reference to a specific resource")
    void t34() {
      ElementDefinition valueX = el(extension, "Extension.value[x]");
      var valueXConstraint = new OnlyRule("value[x]");
      valueXConstraint.types = new ArrayList<>(List.of(ort("Observation", true, null, null)));
      valueX.constrainType(valueXConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Observation"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to version-pinned Resource to be constrained to a reference to a specific resource")
    void t35() {
      // https://github.com/FHIR/sushi/issues/1584
      ElementDefinition valueX = el(extension, "Extension.value[x]");
      var referenceType = valueX.getType().stream().filter(t -> "Reference".equals(t.getCode())).findFirst().orElse(null);
      setAt(referenceType, "targetProfile", list("http://hl7.org/fhir/StructureDefinition/Resource|4.0.1"));
      var valueXConstraint = new OnlyRule("value[x]");
      valueXConstraint.types = new ArrayList<>(List.of(ort("Observation", true, null, null)));
      valueX.constrainType(valueXConstraint, fisher);
      assertEquals(1, size(at(valueX, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Observation"), at(valueX, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to multiple resource types to be constrained to a reference to a subset")
    void t36() {
      ElementDefinition performer = el(observation, "Observation.performer");
      var performerConstraint = new OnlyRule("performer");
      performerConstraint.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null), ort("Organization", true, null, null)));
      performer.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(performer, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/Organization"), at(performer, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to multiple version-pinned resource types to be constrained to a reference to a subset")
    void t37() {
      // https://github.com/FHIR/sushi/issues/1584
      ElementDefinition performer = el(observation, "Observation.performer");
      performer.getType().get(0).setTargetProfile(new ArrayList<>(performer.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|4.0.1").toList()));
      var performerConstraint = new OnlyRule("performer");
      performerConstraint.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null), ort("Organization", true, null, null)));
      performer.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(performer, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/Organization"), at(performer, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should correctly modify _targetProfile array when multiple resource types are constrained to a reference to a subset")
    void t38() {
      ElementDefinition performer = el(modifiedObservation, "Observation.performer");
      var performerConstraint = new OnlyRule("performer");
      performerConstraint.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null), ort("Organization", true, null, null)));
      performer.constrainType(performerConstraint, fisher);
      var expectedType = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/Organization");
      setAt(expectedType, "_targetProfile", list(obj("id", "practitioner-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true))), obj("id", "organization-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", false)))));
      assertEquals(1, size(at(performer, "type")));
      assertJsEqual(expectedType, at(performer, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should remove _targetProfile array when only values are null after constraining types")
    void t39() {
      ElementDefinition performer = el(modifiedObservation, "Observation.performer");
      var performerConstraint = new OnlyRule("performer");
      performerConstraint.types = new ArrayList<>(List.of(ort("PractitionerRole", true, null, null), ort("Patient", true, null, null)));
      performer.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(performer, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/PractitionerRole", "http://hl7.org/fhir/StructureDefinition/Patient"), at(performer, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to multiple resource types to be constrained to a reference to a single type")
    void t40() {
      ElementDefinition performer = el(observation, "Observation.performer");
      var performerConstraint = new OnlyRule("performer");
      performerConstraint.types = new ArrayList<>(List.of(ort("Organization", true, null, null)));
      performer.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(performer, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization"), at(performer, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type in a reference to multiple types to be constrained to a single profile")
    void t41() {
      ElementDefinition subject = el(observation, "Observation.subject");
      var subjectConstraint = new OnlyRule("subject");
      subjectConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/actualgroup", true, null, null)));
      subject.constrainType(subjectConstraint, fisher);
      assertEquals(1, size(at(subject, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/actualgroup"), at(subject, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should null _targetProfile entry when a resource type in a reference is constrained to a single profile")
    void t42() {
      ElementDefinition subject = el(modifiedObservation, "Observation.subject");
      var subjectConstraint = new OnlyRule("subject");
      subjectConstraint.types = new ArrayList<>(List.of(ort("Patient", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/actualgroup", true, null, null)));
      subject.constrainType(subjectConstraint, fisher);
      var expectedType = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Patient", "http://hl7.org/fhir/StructureDefinition/actualgroup");
      setAt(expectedType, "_targetProfile", list(obj("id", "patient-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true))), null));
      assertEquals(1, size(at(subject, "type")));
      assertJsEqual(expectedType, at(subject, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type in a reference to multiple types to be constrained to multiple profiles")
    void t43() {
      ElementDefinition hasMember = el(observation, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should null multiple _targetProfile entries when a resource type in a reference to multiple types is constrained to multiple profiles")
    void t44() {
      ElementDefinition hasMember = el(modifiedObservation, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      var expectedType = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight", "http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse");
      setAt(expectedType, "_targetProfile", list(null, null, obj("id", "questionnaire-response-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true)))));
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(expectedType, at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type in a reference to multiple types to be constrained to with a versioned reference")
    void t45() {
      ElementDefinition subject = el(observation, "Observation.subject");
      var subjectConstraint = new OnlyRule("subject");
      subjectConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/actualgroup|4.0.1", true, null, null), ort("Patient|4.0.1", true, null, null), ort("Device", true, null, null)));
      subject.constrainType(subjectConstraint, fisher);
      assertEquals(1, size(at(subject, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/actualgroup|4.0.1", "http://hl7.org/fhir/StructureDefinition/Patient|4.0.1", "http://hl7.org/fhir/StructureDefinition/Device"), at(subject, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow us to constrain a reference to a profile whose parent is specified using a versioned canonical URL")
    void t46() {
      ElementDefinition hasMember = el(observation, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("https://fhir.kbv.de/StructureDefinition/KBV_PR_Base_Observation_Body_Weight", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("https://fhir.kbv.de/StructureDefinition/KBV_PR_Base_Observation_Body_Weight"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should log a warning when constraining a reference to a profile whose parent is specified using the wrong version")
    void t47() {
      ElementDefinition hasMember = el(observation, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("https://fhir.kbv.de/StructureDefinition/KBV_PR_Base_Observation_Body_Weight-wrong-parent", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("https://fhir.kbv.de/StructureDefinition/KBV_PR_Base_Observation_Body_Weight-wrong-parent"), at(hasMember, "type[0]"));
      assertEquals("https://fhir.kbv.de/StructureDefinition/KBV_PR_Base_Observation_Body_Weight-wrong-parent is based on http://fhir.de/StructureDefinition/observation-de-vitalsign-koerpergewicht version 0.9.34, but SUSHI found version 0.9.13", loggerSpy.getLastMessage("warn"));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type in a reference to multiple types to be constrained to a resource and a single profile")
    void t48() {
      ElementDefinition subject = el(observation, "Observation.subject");
      var subjectConstraint = new OnlyRule("subject");
      subjectConstraint.types = new ArrayList<>(List.of(ort("Patient", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/actualgroup", true, null, null)));
      subject.constrainType(subjectConstraint, fisher);
      assertEquals(1, size(at(subject, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Patient", "http://hl7.org/fhir/StructureDefinition/actualgroup"), at(subject, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to a profile to be constrained to a reference to more specific profiles")
    void t49() {
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to a profile to be constrained to a reference to a profile that declares it in an imposeProfile extension")
    void t50() {
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      profile.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile);
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("SomeOtherObsProfile", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a profile is constrained to a reference to a profile that does not formally extend it nor declare it in an imposeProfile extension")
    void t51() {
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      exporter.exportStructDef(profile);
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      ElementDefinition hasMemberClone = hasMember.clone(false);
      assertThrowsMatching("\"Reference\\(SomeOtherObsProfile\\)\" does not match .* Reference\\(\\S+\\/QuestionnaireResponse \\| \\S+\\/MolecularSequence \\| \\S+\\/vitalsigns\\)", () -> { var hasMemberConstraint = new OnlyRule("hasMember");
hasMemberConstraint.types = new ArrayList<>(List.of(ort("SomeOtherObsProfile", true, null, null)));
hasMember.constrainType(hasMemberConstraint, fisher);
 });
      assertSameState(hasMemberClone, hasMember);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to a profile to be constrained to multiple references to profiles that declare it in imposeProfile extensions")
    void t52() {
      var profile1 = new Profile("AnObsProfile");
      profile1.parent = "Observation";
      profile1.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile1);
      var profile2 = new Profile("SomeOtherObsProfile");
      profile2.parent = "Observation";
      profile2.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile2);
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("AnObsProfile", true, null, null), ort("SomeOtherObsProfile", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/AnObsProfile", "http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a profile is constrained to multiple references to profiles that do not formally extend it nor declare it in imposeProfile extensions")
    void t53() {
      var profile1 = new Profile("AnObsProfile");
      profile1.parent = "Observation";
      exporter.exportStructDef(profile1);
      var profile2 = new Profile("SomeOtherObsProfile");
      profile2.parent = "Observation";
      exporter.exportStructDef(profile2);
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      ElementDefinition hasMemberClone = hasMember.clone(false);
      assertThrowsMatching("\"Reference\\(AnObsProfile\\)\" does not match .* Reference\\(\\S+\\/QuestionnaireResponse \\| \\S+\\/MolecularSequence \\| \\S+\\/vitalsigns\\)", () -> { var hasMemberConstraint = new OnlyRule("hasMember");
hasMemberConstraint.types = new ArrayList<>(List.of(ort("AnObsProfile", true, null, null), ort("SomeOtherObsProfile", true, null, null)));
hasMember.constrainType(hasMemberConstraint, fisher);
 });
      assertSameState(hasMemberClone, hasMember);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to a profile to be constrained to multiple references to a profile when some formally extend it and some declare it in imposeProfile extensions")
    void t54() {
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      profile.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile);
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("SomeOtherObsProfile", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher);
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a profile is constrained to multiple references to profiles that formally extend it and profiles that do not extend it nor declare it in imposeProfile extensions")
    void t55() {
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      exporter.exportStructDef(profile);
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      ElementDefinition hasMemberClone = hasMember.clone(false);
      assertThrowsMatching("\"Reference\\(SomeOtherObsProfile\\)\" does not match .* Reference\\(\\S+\\/QuestionnaireResponse \\| \\S+\\/MolecularSequence \\| \\S+\\/vitalsigns\\)", () -> { var hasMemberConstraint = new OnlyRule("hasMember");
hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("SomeOtherObsProfile", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
hasMember.constrainType(hasMemberConstraint, fisher);
 });
      assertSameState(hasMemberClone, hasMember);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to Any to be constrained to a reference to a resource")
    void t56() {
      ElementDefinition focus = el(observation, "Observation.focus");
      var focusConstraint = new OnlyRule("focus");
      focusConstraint.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null)));
      focus.constrainType(focusConstraint, fisher);
      assertEquals(1, size(at(focus, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner"), at(focus, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should remove _targetProfile when a reference to Any is constrained to a reference to a resource")
    void t57() {
      ElementDefinition focus = el(modifiedObservation, "Observation.focus");
      var focusConstraint = new OnlyRule("focus");
      focusConstraint.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null)));
      focus.constrainType(focusConstraint, fisher);
      assertEquals(1, size(at(focus, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner"), at(focus, "type[0]"));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to Any to be constrained to a reference to a profile")
    void t58() {
      ElementDefinition focus = el(observation, "Observation.focus");
      var focusConstraint = new OnlyRule("focus");
      focusConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bp", true, null, null)));
      focus.constrainType(focusConstraint, fisher);
      assertEquals(1, size(at(focus, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bp"), at(focus, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to Any to be constrained to multiple references")
    void t59() {
      ElementDefinition focus = el(observation, "Observation.focus");
      var focusConstraint = new OnlyRule("focus");
      focusConstraint.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bp", true, null, null)));
      focus.constrainType(focusConstraint, fisher);
      assertEquals(1, size(at(focus, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/bp"), at(focus, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to multiple resource types to be constrained such that only the target reference is constrained and others remain as-is")
    void t60() {
      ElementDefinition hasMember = el(observation, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher, "Observation");
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight", "http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse", "http://hl7.org/fhir/StructureDefinition/MolecularSequence"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a reference to multiple resource types to be constrained such that only the target reference is constrained and others remain as-is including those who declare imposeProfile extensions")
    void t61() {
      var profile = new Profile("SomeOtherObsProfile");
      profile.parent = "Observation";
      profile.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/bp"));
      exporter.exportStructDef(profile);
      var jsonVitalSigns = defs.fishForFHIR("http://hl7.org/fhir/StructureDefinition/vitalsigns", FishableType.Profile);
      StructureDefinition vitalSigns = StructureDefinition.fromJSON(jsonVitalSigns);
      ElementDefinition hasMember = el(vitalSigns, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("SomeOtherObsProfile", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher, "http://hl7.org/fhir/StructureDefinition/vitalsigns");
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse", "http://hl7.org/fhir/StructureDefinition/MolecularSequence", "http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherObsProfile", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should null multiple _targetProfile entries when a reference to multiple resource types is constrained such that only the target reference is constrained and others remain as-is")
    void t62() {
      ElementDefinition hasMember = el(modifiedObservation, "Observation.hasMember");
      var hasMemberConstraint = new OnlyRule("hasMember");
      hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
      hasMember.constrainType(hasMemberConstraint, fisher, "Observation");
      var expectedType = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight", "http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse", "http://hl7.org/fhir/StructureDefinition/MolecularSequence");
      setAt(expectedType, "_targetProfile", list(null, null, obj("id", "questionnaire-response-extra", "extension", list(obj("url", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true))), null));
      assertEquals(1, size(at(hasMember, "type")));
      assertJsEqual(expectedType, at(hasMember, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }
    // start canonicals

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to multiple resource types to be constrained to a canonical to a subset")
    void t63() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("ActivityDefinition", null, true, null), ort("Questionnaire", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/ActivityDefinition", "http://hl7.org/fhir/StructureDefinition/Questionnaire"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to multiple version-pinned resource types to be constrained to a canonical to a subset")
    void t64() {
      // https://github.com/FHIR/sushi/issues/1584
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      actionDef.getType().get(0).setTargetProfile(new ArrayList<>(actionDef.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|4.0.1").toList()));
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("ActivityDefinition", null, true, null), ort("Questionnaire", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/ActivityDefinition", "http://hl7.org/fhir/StructureDefinition/Questionnaire"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to multiple resource types to be constrained with a versioned canonical")
    void t65() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("ActivityDefinition", null, true, null), ort("Questionnaire|4.0.1", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/ActivityDefinition", "http://hl7.org/fhir/StructureDefinition/Questionnaire|4.0.1"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to multiple version-pinned resource types to be constrained with a versioned canonical")
    void t66() {
      // https://github.com/FHIR/sushi/issues/1584
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      actionDef.getType().get(0).setTargetProfile(new ArrayList<>(actionDef.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|4.0.1").toList()));
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("ActivityDefinition", null, true, null), ort("Questionnaire|4.0.1", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/ActivityDefinition", "http://hl7.org/fhir/StructureDefinition/Questionnaire|4.0.1"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to multiple resource types to be constrained to a canonical to a single type")
    void t67() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("Questionnaire", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Questionnaire"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type in a canonical to multiple types to be constrained to a single profile")
    void t68() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/shareableplandefinition", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/shareableplandefinition"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type in a canonical to multiple types to be constrained to multiple profiles")
    void t69() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/shareableplandefinition", null, true, null), ort("http://hl7.org/fhir/StructureDefinition/computableplandefinition", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/shareableplandefinition", "http://hl7.org/fhir/StructureDefinition/computableplandefinition"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a resource type in a canonical to multiple types to be constrained to a resource and a single profile")
    void t70() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/ActivityDefinition", null, true, null), ort("http://hl7.org/fhir/StructureDefinition/computableplandefinition", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/ActivityDefinition", "http://hl7.org/fhir/StructureDefinition/computableplandefinition"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to a profile to be constrained to a canonical to a more specific profile")
    void t71() {
      var constrainedPlanDefinition = Js.cloneDeep(planDefinition);
      ElementDefinition actionDef = el(constrainedPlanDefinition, "PlanDefinition.action.definition[x]");
      setAt(actionDef, "type", list(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/shareableplandefinition")));
      var profile = new Profile("ProfileOfShareablePlanDefinition");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/shareableplandefinition";
      exporter.exportStructDef(profile);
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("ProfileOfShareablePlanDefinition", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileOfShareablePlanDefinition"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to a profile to be constrained to a canonical to a profile that declares it in an imposeProfile extension")
    void t72() {
      var constrainedPlanDefinition = Js.cloneDeep(planDefinition);
      ElementDefinition actionDef = el(constrainedPlanDefinition, "PlanDefinition.action.definition[x]");
      setAt(actionDef, "type", list(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/shareableplandefinition")));
      var profile = new Profile("SomeOtherPlanDefinitionProfile");
      profile.parent = "PlanDefinition";
      profile.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/shareableplandefinition"));
      exporter.exportStructDef(profile);
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("SomeOtherPlanDefinitionProfile", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher);
      assertEquals(1, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherPlanDefinitionProfile"), at(actionDef, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to Any to be constrained to a canonical to a resource")
    void t73() {
      ElementDefinition value = el(extension, "Extension.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("Practitioner", null, true, null)));
      value.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(value, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner"), at(value, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to Any to be constrained to a canonical to a profile")
    void t74() {
      ElementDefinition value = el(extension, "Extension.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bp", null, true, null)));
      value.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(value, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bp"), at(value, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to Any to be constrained to multiple canonicals")
    void t75() {
      ElementDefinition value = el(extension, "Extension.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/Practitioner", null, true, null), ort("http://hl7.org/fhir/StructureDefinition/bp", null, true, null)));
      value.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(value, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/bp"), at(value, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to Any to be constrained to a canonical with a version")
    void t76() {
      ElementDefinition value = el(extension, "Extension.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/Practitioner|4.0.1", null, true, null)));
      value.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(value, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner|4.0.1"), at(value, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to Any to be constrained to a profile with a version")
    void t77() {
      ElementDefinition value = el(extension, "Extension.value[x]");
      var valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bp|4.0.1", null, true, null)));
      value.constrainType(valueConstraint, fisher);
      assertEquals(1, size(at(value, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bp|4.0.1"), at(value, "type[0]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @Disabled("skipped in TS")
    @DisplayName("ElementDefinition #constrainType() should output the canonical with the version preceding the fragment when the url has both a version and a fragment")
    void t78() {
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should allow a canonical to multiple resource types to be constrained such that only the target reference is constrained and others remain as-is")
    void t79() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      var performerConstraint = new OnlyRule("action.definition[x]");
      performerConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/shareableplandefinition", null, true, null), ort("http://hl7.org/fhir/StructureDefinition/computableplandefinition", null, true, null)));
      actionDef.constrainType(performerConstraint, fisher, "PlanDefinition");
      assertEquals(2, size(at(actionDef, "type")));
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/ActivityDefinition", "http://hl7.org/fhir/StructureDefinition/shareableplandefinition", "http://hl7.org/fhir/StructureDefinition/computableplandefinition", "http://hl7.org/fhir/StructureDefinition/Questionnaire"), at(actionDef, "type[0]"));
      assertJsEqual(new ElementDefinitionType("uri"), at(actionDef, "type[1]"));
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }
    // end canonicals

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a passed in type cannot constrain any existing types")
    void t80() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      ElementDefinition clone = valueX.clone(false);
      assertThrowsMatching("\"decimal\" does not match .* Quantity or CodeableConcept or string", () -> { var valueConstraint = new OnlyRule("value[x]");
valueConstraint.types = new ArrayList<>(List.of(ort("decimal", null, null, null)));
clone.constrainType(valueConstraint, fisher);
 });
      assertSameState(valueX, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when passed in a reference to a type that cannot constrain any existing references to types")
    void t81() {
      ElementDefinition performer = el(observation, "Observation.performer");
      ElementDefinition clone = performer.clone(false);
      assertThrowsMatching("\"Reference\\(Medication\\)\" does not match .* Reference\\(http:\\/\\/hl7.org\\/fhir\\/StructureDefinition\\/Practitioner | http:\\/\\/hl7.org\\/fhir\\/StructureDefinition\\/PractitionerRole .*\\)", () -> { var performerConstraint = new OnlyRule("performer");
performerConstraint.types = new ArrayList<>(List.of(ort("Medication", true, null, null)));
clone.constrainType(performerConstraint, fisher);
 });
      assertSameState(performer, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when passed in a reference to a version-pinned type that cannot constrain any existing references to version-pinned types")
    void t82() {
      ElementDefinition performer = el(observation, "Observation.performer");
      performer.getType().get(0).setTargetProfile(new ArrayList<>(performer.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|4.1.0").toList()));
      ElementDefinition clone = performer.clone(false);
      assertThrowsContaining("The type \"Reference(Practitioner|4.0.1)\" does not match any of the allowed types: Reference(http://hl7.org/fhir/StructureDefinition/Practitioner|4.1.0 | http://hl7.org/fhir/StructureDefinition/PractitionerRole|4.1.0 | http://hl7.org/fhir/StructureDefinition/Organization|4.1.0 | http://hl7.org/fhir/StructureDefinition/CareTeam|4.1.0 | http://hl7.org/fhir/StructureDefinition/Patient|4.1.0 | http://hl7.org/fhir/StructureDefinition/RelatedPerson|4.1.0)", () -> { var performerConstraint = new OnlyRule("performer");
performerConstraint.types = new ArrayList<>(List.of(ort("Practitioner|4.0.1", true, null, null)));
clone.constrainType(performerConstraint, fisher);
 });
      assertSameState(performer, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when a passed in versioned canonical does not match the version-pinned canonical")
    void t83() {
      ElementDefinition actionDef = el(planDefinition, "PlanDefinition.action.definition[x]");
      actionDef.getType().get(0).setTargetProfile(new ArrayList<>(actionDef.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|4.1.0").toList()));
      ElementDefinition clone = actionDef.clone(false);
      assertThrowsContaining("The type \"Canonical(ActivityDefinition|4.0.1)\" does not match any of the allowed types: Canonical(http://hl7.org/fhir/StructureDefinition/ActivityDefinition|4.1.0 | http://hl7.org/fhir/StructureDefinition/PlanDefinition|4.1.0 | http://hl7.org/fhir/StructureDefinition/Questionnaire|4.1.0) or uri", () -> { var performerConstraint = new OnlyRule("action.definition[x]");
performerConstraint.types = new ArrayList<>(List.of(ort("ActivityDefinition|4.0.1", null, true, null), ort("Questionnaire|4.0.1", null, true, null)));
actionDef.constrainType(performerConstraint, fisher);
 });
      assertSameState(actionDef, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when attempting to constrain Resource to a reference")
    void t84() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      ElementDefinition clone = entryResource.clone(false);
      assertThrowsMatching("\"Reference\\(Procedure\\)\" does not match .* Resource", () -> { var resourceConstraint = new OnlyRule("entry.resource");
resourceConstraint.types = new ArrayList<>(List.of(ort("Procedure", true, null, null)));
clone.constrainType(resourceConstraint, fisher);
 });
      assertSameState(entryResource, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when the targetType does not match any existing types")
    void t85() {
      ElementDefinition hasMember = el(observation, "Observation.hasMember");
      ElementDefinition clone = hasMember.clone(false);
      assertThrowsMatching("\"FamilyMemberHistory\" does not match .* Reference\\(http:\\/\\/hl7.org\\/fhir\\/StructureDefinition\\/Observation .*\\)", () -> { var hasMemberConstraint = new OnlyRule("hasMember");
hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/familymemberhistory-genetic", true, null, null)));
clone.constrainType(hasMemberConstraint, fisher, "FamilyMemberHistory");
 });
      assertSameState(hasMember, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when the passed in type does not match the targetType")
    void t86() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      ElementDefinition clone = valueX.clone(false);
      assertThrowsMatching("\"SimpleQuantity\" does not match .* CodeableConcept", () -> { var valueConstraint = new OnlyRule("value[x]");
valueConstraint.types = new ArrayList<>(List.of(ort("SimpleQuantity", null, null, null)));
clone.constrainType(valueConstraint, fisher, "CodeableConcept");
 });
      assertSameState(valueX, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when the passed in reference type does not match the targetType")
    void t87() {
      ElementDefinition hasMember = el(observation, "Observation.hasMember");
      ElementDefinition clone = hasMember.clone(false);
      assertThrowsMatching("\"Reference\\(http:\\/\\/hl7.org\\/fhir\\/StructureDefinition\\/bodyheight\\)\" does not match .* Reference\\(http:\\/\\/hl7.org\\/fhir\\/StructureDefinition\\/QuestionnaireResponse\\)", () -> { var hasMemberConstraint = new OnlyRule("hasMember");
hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null)));
clone.constrainType(hasMemberConstraint, fisher, "QuestionnaireResponse");
 });
      assertSameState(hasMember, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw InvalidTypeError when attempting to constrain a reference when the target type is Resource")
    void t88() {
      StructureDefinition bundle = fisher.fishForStructureDefinition("Bundle");
      ElementDefinition entryResource = el(bundle, "Bundle.entry.resource");
      ElementDefinition clone = entryResource.clone(false);
      assertThrowsMatching("\"Reference\\(Procedure\\)\" does not match .* Resource", () -> { var resourceConstraint = new OnlyRule("entry.resource");
resourceConstraint.types = new ArrayList<>(List.of(ort("Procedure", true, null, null)));
clone.constrainType(resourceConstraint, fisher, "Resource");
 });
      assertSameState(entryResource, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw TypeNotFoundError when a passed in type definition cannot be found")
    void t89() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      ElementDefinition clone = valueX.clone(false);
      assertThrowsMatching("No definition for the type \"Monocle\" could be found.", () -> { var valueConstraint = new OnlyRule("value[x]");
valueConstraint.types = new ArrayList<>(List.of(ort("Quantity", null, null, null), ort("Monocle", null, null, null)));
clone.constrainType(valueConstraint, fisher);
 });
      assertSameState(valueX, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw TypeNotFoundError when a passed in reference types definition cannot be found")
    void t90() {
      ElementDefinition performer = el(observation, "Observation.performer");
      ElementDefinition clone = performer.clone(false);
      assertThrowsMatching("No definition for the type \"Juggler\" could be found.", () -> { var performerConstraint = new OnlyRule("performer");
performerConstraint.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null), ort("Juggler", true, null, null)));
clone.constrainType(performerConstraint, fisher);
 });
      assertSameState(performer, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw TypeNotFoundError when the targetType definition cannot be found")
    void t91() {
      ElementDefinition hasMember = el(observation, "Observation.hasMember");
      ElementDefinition clone = hasMember.clone(false);
      assertThrowsMatching("No definition for the type \"VitalBillboards\" could be found.", () -> { var hasMemberConstraint = new OnlyRule("hasMember");
hasMemberConstraint.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null)));
clone.constrainType(hasMemberConstraint, fisher, "VitalBillboards");
 });
      assertSameState(hasMember, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }

    @Test
    @DisplayName("ElementDefinition #constrainType() should throw NonAbstractParentError when constraining a non-abstract parent to a specialization of it")
    void t92() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      ElementDefinition clone = valueX.clone(false);
      assertThrowsMatching("The type Quantity is not abstract, so it cannot be constrained to the specialization Duration", () -> { var valueConstraint = new OnlyRule("value[x]");
valueConstraint.types = new ArrayList<>(List.of(ort("Duration", null, null, null)));
clone.constrainType(valueConstraint, fisher);
 });
      assertSameState(valueX, clone);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals(0, size(loggerSpy.getAllLogs("error")));
    }
  }
}
@DisplayName("ElementDefinition R5")
class ElementDefinitionConstrainTypeR5Test {
  static CaretValueRule imposeProfileRule(Object imposeProfileURL) {
    return ElementDefinitionConstrainTypeTest.imposeProfileRule(imposeProfileURL);
  }

  static FHIRDefinitions defs;
  StructureDefinition r5CarePlan;
  StructureDefinition r5Extension;
  static StructureDefinitionExporter exporter;
  static TestFisher fisher;
  static Package pkg;
  @BeforeAll
  static void beforeAll93() {
    defs = r5Defs();
    pkg = new Package(minimalConfig());
    fisher = new TestFisher().withFHIR(defs).withPackage(pkg);
    exporter = new StructureDefinitionExporter(new FSHTank(new ArrayList<>(), minimalConfig()), pkg, fisher);
  }
  @BeforeEach
  void beforeEach94() {
    r5CarePlan = fisher.fishForStructureDefinition("CarePlan");
    r5Extension = fisher.fishForStructureDefinition("Extension");
    loggerSpy.reset();
    pkg.clearAllDefinitions();
  }

  @Nested
  @DisplayName("#constrainType()")
  class ConstrainType {

    @Nested
    @DisplayName("Reference() keyword")
    class ReferenceKeyword {

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should warn when a CodeableReference to multiple resource types is constrained to a Reference to a subset")
      void t95() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null), ort("Organization", true, null, null)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/Organization"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(1, size(loggerSpy.getAllMessages("warn")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should warn when a CodeableReference to multiple version-pinned resource types is constrained to a Reference to a subset")
      void t96() {
        // https://github.com/FHIR/sushi/issues/1584
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        performedActivity.getType().get(0).setTargetProfile(new ArrayList<>(performedActivity.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|5.0.0").toList()));
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null), ort("Organization", true, null, null)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/Organization"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(1, size(loggerSpy.getAllMessages("warn")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should warn when a CodeableReference to multiple resource types is constrained to a Reference to a single type")
      void t97() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(1, size(loggerSpy.getAllMessages("warn")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should warn when a CodeableReference to multiple resource types is constrained to a Reference to a single profile")
      void t98() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/actualgroup", true, null, null)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/actualgroup"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(1, size(loggerSpy.getAllMessages("warn")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should warn when a CodeableReference to multiple resource types is constrained to a Reference to a multiple profiles")
      void t99() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", true, null, null), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", true, null, null)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(1, size(loggerSpy.getAllMessages("warn")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should constrain an element to Reference type with the Reference() keyword when both Reference and CodeableReference is allowed")
      void t100() {
        // R5 Extension allows both Reference and CodeableReference types
        StructureDefinition r5Extension = fisher.fishForStructureDefinition("Extension");
        ElementDefinition valueX = el(r5Extension, "Extension.value[x]");
        // * value[x] only Reference(Resource)
        var onlyRule = new OnlyRule("value[x]");
        onlyRule.types = new ArrayList<>(List.of(ort("Resource", true, null, null)));
        valueX.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(valueX, "type")));
        assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Resource"), at(valueX, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should warn when constraining an element to CodeableReference type with a Reference() keyword when multiple types including CodeableReference and not including Reference are allowed")
      void t101() {
        // R5 Extension allows both Reference and CodeableReference types
        StructureDefinition r5Extension = fisher.fishForStructureDefinition("Extension");
        ElementDefinition valueX = el(r5Extension, "Extension.value[x]");
        // * value[x] only string or CodeableReference
        var onlyRule = new OnlyRule("value[x]");
        onlyRule.types = new ArrayList<>(List.of(ort("string", null, null, null), ort("CodeableReference", null, null, null)));
        valueX.constrainType(onlyRule, fisher);
        // * value[x] only string or Reference(Resource)
        var onlyRule2 = new OnlyRule("value[x]");
        onlyRule2.types = new ArrayList<>(List.of(ort("string", null, null, null), ort("Resource", true, null, null)));
        valueX.constrainType(onlyRule2, fisher);
        assertEquals(2, size(at(valueX, "type")));
        assertJsEqual(new ElementDefinitionType("string"), at(valueX, "type[0]"));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Resource"), at(valueX, "type[1]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(1, size(loggerSpy.getAllMessages("warn")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should throw InvalidTypeError when a passed in reference to a type that cannot constrain any existing references to types on a CodeableReference")
      void t102() {
        ElementDefinition addresses = el(r5CarePlan, "CarePlan.addresses");
        var onlyRule = new OnlyRule("addresses");
        onlyRule.types = new ArrayList<>(List.of(ort("Patient", true, null, null)));
        assertThrowsMatching("\"Reference\\(Patient\\).*Reference\\(.*Condition\\)", () -> { addresses.constrainType(onlyRule, fisher);
 });
        assertEquals(0, size(loggerSpy.getAllLogs("error")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() Reference() keyword should throw InvalidTypeError when a passed in reference to a version-pinned type that cannot constrain any existing references to version-pinned types on a CodeableReference")
      void t103() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        performedActivity.getType().get(0).setTargetProfile(new ArrayList<>(performedActivity.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|4.0.1").toList()));
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner|5.0.0", true, null, null), ort("Organization|5.0.0", true, null, null)));
        assertThrowsMatching("\"Reference\\(Practitioner\\|5.0.0\\).*CodeableReference\\(.*Resource\\|4.0.1\\)", () -> { performedActivity.constrainType(onlyRule, fisher);
 });
        assertEquals(0, size(loggerSpy.getAllLogs("error")));
        assertMatchesJs("CodeableReference\\(\\) keyword should be used", loggerSpy.getLastMessage("warn"));
      }
    }

    @Nested
    @DisplayName("CodeableReference() keyword")
    class CodeableReferenceKeyword {

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a reference to Resource to be constrained to a reference to a specific resource")
      void t104() {
        ElementDefinition valueX = el(r5Extension, "Extension.value[x]");
        var valueXConstraint = new OnlyRule("value[x]");
        valueXConstraint.types = new ArrayList<>(List.of(ort("Observation", null, null, true)));
        valueX.constrainType(valueXConstraint, fisher);
        assertEquals(1, size(at(valueX, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Observation"), at(valueX, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllLogs("warn")));
        assertEquals(0, size(loggerSpy.getAllLogs("error")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a reference to version-pinned Resource to be constrained to a reference to a specific resource")
      void t105() {
        // https://github.com/FHIR/sushi/issues/1584
        ElementDefinition valueX = el(r5Extension, "Extension.value[x]");
        var referenceType = valueX.getType().stream().filter(t -> "Reference".equals(t.getCode())).findFirst().orElse(null);
        setAt(referenceType, "targetProfile", list("http://hl7.org/fhir/StructureDefinition/Resource|5.0.0"));
        var valueXConstraint = new OnlyRule("value[x]");
        valueXConstraint.types = new ArrayList<>(List.of(ort("Observation", null, null, true)));
        valueX.constrainType(valueXConstraint, fisher);
        assertEquals(1, size(at(valueX, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Observation"), at(valueX, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllLogs("warn")));
        assertEquals(0, size(loggerSpy.getAllLogs("error")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to multiple resource types to be constrained to a reference to a subset")
      void t106() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner", null, null, true), ort("Organization", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/Organization"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to multiple version-pinned resource types to be constrained to a reference to a subset")
      void t107() {
        // https://github.com/FHIR/sushi/issues/1584
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        performedActivity.getType().get(0).setTargetProfile(new ArrayList<>(performedActivity.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|5.0.0").toList()));
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner", null, null, true), ort("Organization", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner", "http://hl7.org/fhir/StructureDefinition/Organization"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to multiple resource types to be constrained to a reference to a single type")
      void t108() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to multiple resource types to be constrained to a reference to a single profile")
      void t109() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/actualgroup", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/actualgroup"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to multiple resource types to be constrained to a reference to multiple profiles")
      void t110() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("http://hl7.org/fhir/StructureDefinition/bodyheight", null, null, true), ort("http://hl7.org/fhir/StructureDefinition/bodyweight", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/bodyheight", "http://hl7.org/fhir/StructureDefinition/bodyweight"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to a profile to be constrained to a more specific profile")
      void t111() {
        var constrainedR5CarePlan = Js.cloneDeep(r5CarePlan);
        ElementDefinition performedActivity = el(constrainedR5CarePlan, "CarePlan.activity.performedActivity");
        setAt(performedActivity, "type", list(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/actualgroup")));
        var profile = new Profile("ProfileOfActualGroup");
        profile.parent = "http://hl7.org/fhir/StructureDefinition/actualgroup";
        exporter.exportStructDef(profile);
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("ProfileOfActualGroup", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileOfActualGroup"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to a profile to be constrained to a CodeableReference to a profile that declares it in an imposeProfile extension")
      void t112() {
        var constrainedR5CarePlan = Js.cloneDeep(r5CarePlan);
        ElementDefinition performedActivity = el(constrainedR5CarePlan, "CarePlan.activity.performedActivity");
        setAt(performedActivity, "type", list(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/actualgroup")));
        var profile = new Profile("SomeOtherGroupProfile");
        profile.parent = "Group";
        profile.rules.add(imposeProfileRule("http://hl7.org/fhir/StructureDefinition/actualgroup"));
        exporter.exportStructDef(profile);
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("SomeOtherGroupProfile", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/us/minimal/StructureDefinition/SomeOtherGroupProfile"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllLogs("warn")));
        assertEquals(0, size(loggerSpy.getAllLogs("error")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should constrain an element to CodeableReference type with the CodeableReference() keyword when both Reference and CodeableReference is allowed")
      void t113() {
        // R5 Extension allows both Reference and CodeableReference types
        StructureDefinition r5Extension = fisher.fishForStructureDefinition("Extension");
        ElementDefinition valueX = el(r5Extension, "Extension.value[x]");
        // * value[x] only CodeableReference(Resource)
        var onlyRule = new OnlyRule("value[x]");
        onlyRule.types = new ArrayList<>(List.of(ort("Resource", null, null, true)));
        valueX.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(valueX, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Resource"), at(valueX, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should constrain an element to a combination of CodeableReference, Reference, and non-reference types")
      void t114() {
        // R5 Extension allows both Reference and CodeableReference types
        StructureDefinition r5Extension = fisher.fishForStructureDefinition("Extension");
        ElementDefinition valueX = el(r5Extension, "Extension.value[x]");
        // * value[x] only CodeableReference(Resource) or Reference(Observation) or boolean
        var onlyRule = new OnlyRule("value[x]");
        onlyRule.types = new ArrayList<>(List.of(ort("Resource", null, null, true), ort("Observation", true, null, null), ort("string", false, null, false)));
        valueX.constrainType(onlyRule, fisher);
        assertEquals(3, size(at(valueX, "type")));
        assertJsEqual(new ElementDefinitionType("string"), at(valueX, "type[0]"));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Resource"), at(valueX, "type[1]"));
        assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Observation"), at(valueX, "type[2]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should allow a CodeableReference to multiple resource types to be constrained to a versioned reference")
      void t115() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner|5.0.0", null, null, true), ort("http://hl7.org/fhir/StructureDefinition/Group|5.0.0", null, null, true), ort("Organization", null, null, true)));
        performedActivity.constrainType(onlyRule, fisher);
        assertEquals(1, size(at(performedActivity, "type")));
        assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner|5.0.0", "http://hl7.org/fhir/StructureDefinition/Group|5.0.0", "http://hl7.org/fhir/StructureDefinition/Organization"), at(performedActivity, "type[0]"));
        assertEquals(0, size(loggerSpy.getAllMessages("error")));
        assertEquals(0, size(loggerSpy.getAllMessages("warn")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should throw InvalidTypeError when a passed in reference to a type that cannot constrain any existing references to types on a CodeableReference using CodeableReference keyword")
      void t116() {
        ElementDefinition addresses = el(r5CarePlan, "CarePlan.addresses");
        var onlyRule = new OnlyRule("addresses");
        onlyRule.types = new ArrayList<>(List.of(ort("Patient", null, null, true)));
        assertThrowsMatching("\"CodeableReference\\(Patient\\).*CodeableReference\\(.*Condition\\)", () -> { addresses.constrainType(onlyRule, fisher);
 });
        assertEquals(0, size(loggerSpy.getAllLogs("warn")));
        assertEquals(0, size(loggerSpy.getAllLogs("error")));
      }

      @Test
      @DisplayName("ElementDefinition R5 #constrainType() CodeableReference() keyword should throw InvalidTypeError when a passed in reference to a version-pinned type that cannot constrain any existing references to version-pinned types on a CodeableReference using CodeableReference keyword")
      void t117() {
        ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
        performedActivity.getType().get(0).setTargetProfile(new ArrayList<>(performedActivity.getType().get(0).getTargetProfile().stream().map(tp -> tp + "|4.0.1").toList()));
        var onlyRule = new OnlyRule("activity.performedActivity");
        onlyRule.types = new ArrayList<>(List.of(ort("Practitioner|5.0.0", null, null, true), ort("Organization|5.0.0", null, null, true)));
        assertThrowsMatching("\"CodeableReference\\(Practitioner\\|5.0.0\\).*CodeableReference\\(.*Resource\\|4.0.1\\)", () -> { performedActivity.constrainType(onlyRule, fisher);
 });
        assertEquals(0, size(loggerSpy.getAllLogs("warn")));
        assertEquals(0, size(loggerSpy.getAllLogs("error")));
      }
    }

    @Test
    @DisplayName("ElementDefinition R5 #constrainType() should log an error when trying to constrain the reference portion of a CodeableReference element directly")
    void t118() {
      ElementDefinition performedActivity = el(r5CarePlan, "CarePlan.activity.performedActivity");
      performedActivity.unfold(fisher);
      ElementDefinition reference = el(r5CarePlan, "CarePlan.activity.performedActivity.reference");
      OnlyRule onlyRule = new OnlyRule("activity.performedActivity.reference").withFile("fishy.fsh").withLocation(new TextLocation(6, 1, 6, 20));
      onlyRule.types = new ArrayList<>(List.of(ort("Practitioner", true, null, null)));
      reference.constrainType(onlyRule, fisher);
      // applies the constraint author specified, even though the spec says this should not be done
      assertEquals(1, size(at(reference, "type")));
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Practitioner"), at(reference, "type[0]"));
      // log an error because the author should not have constrained reference directly
      assertEquals(1, size(loggerSpy.getAllMessages("error")));
      assertMatchesJs("(?s)Constraining references on a CodeableReference element's underlying \\.reference path is not allowed.* directly on the CodeableReference element", loggerSpy.getLastMessage("error"));
      assertMatchesJs("(?s)File: fishy\\.fsh.*Line: 6\\D*", loggerSpy.getLastMessage("error"));
      assertEquals(0, size(loggerSpy.getAllMessages("warn")));
    }
  }
}
