package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hl7.fhir.sushi.errors.MismatchedTypeError;
import org.hl7.fhir.sushi.export.StructureDefinitionExporter.DeferredCaretRule;
import org.hl7.fhir.sushi.export.StructureDefinitionExporter.KnownBindingRule;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinition.ProfileExporter.test.ts (SUSHI 3.21.1), describe 'ProfileExporter'
// The TS beforeEach is the same as SdeTestBase's; only the beforeAll defs differ (r4-definitions only).
@DisplayName("ProfileExporter")
class StructureDefinitionProfileExporterTest extends SdeTestBase {

  @Override
  TestFHIRDefinitions loadDefs() {
    return defs(true, testDefsPath("r4-definitions"));
  }

  @Test
  @DisplayName("ProfileExporter should output empty results with empty input")
  void emptyInput() {
    List<StructureDefinition> exported = exporter.export().profiles;
    assertJsEqual(List.of(), exported);
  }

  @Test
  @DisplayName("ProfileExporter should export a single profile")
  void singleProfile() {
    Profile profile = new Profile("Foo");
    profile.parent = "Basic";
    doc.profiles.put(profile.name, profile);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
  }

  @Test
  @DisplayName("ProfileExporter should add source info for the exported profile to the package")
  void sourceInfoInFshMap() {
    Profile profile = new Profile("Foo").<Profile>withFile("SomeProfiles.fsh").withLocation(28, 4, 39, 15);
    profile.parent = "Basic";
    doc.profiles.put(profile.name, profile);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertEquals(
        new Package.FshMapEntry(new SourceInfo("SomeProfiles.fsh", new TextLocation(28, 4, 39, 15)), "Foo",
            "Profile"),
        pkg.fshMap.get("StructureDefinition-Foo.json"));
  }

  @Test
  @DisplayName("ProfileExporter should export multiple profiles")
  void multipleProfiles() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Basic";
    Profile profileBar = new Profile("Bar");
    profileBar.parent = "Basic";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.profiles.put(profileBar.name, profileBar);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(2, exported.size());
  }

  @Test
  @DisplayName("ProfileExporter should still export profiles if one fails")
  void stillExportIfOneFails() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Baz";
    Profile profileBar = new Profile("Bar");
    profileBar.parent = "Basic";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.profiles.put(profileBar.name, profileBar);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertEquals("Bar", at(exported.get(0), "name"));
  }

  @Test
  @DisplayName("ProfileExporter should log a error with source information when the parent is not found")
  void parentNotFound() {
    Profile profile = new Profile("Bogus").<Profile>withFile("Bogus.fsh").withLocation(2, 9, 4, 23);
    profile.parent = "BogusParent";
    doc.profiles.put(profile.name, profile);
    exporter.export();
    assertMatches("File: Bogus\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Parent BogusParent not found for Bogus", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ProfileExporter should log a error with source information when the parent is not provided")
  void parentNotProvided() {
    Profile profile = new Profile("Missing").<Profile>withFile("Missing.fsh").withLocation(2, 9, 4, 23);
    doc.profiles.put(profile.name, profile);
    exporter.export();
    assertMatches("File: Missing\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("The definition for Missing does not include a Parent", "s", loggerSpy.getLastMessage("error"));
  }

  // Note: the TS tests below write `expect(a.baseDefinition === b.url);` without a matcher, which asserts nothing;
  // those lines are kept as comments.

  @Test
  @DisplayName("ProfileExporter should export profiles with FSHy parents")
  void fshyParents() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Basic";
    Profile profileBar = new Profile("Bar");
    profileBar.parent = "Foo";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.profiles.put(profileBar.name, profileBar);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
  }

  @Test
  @DisplayName("ProfileExporter should export profiles with the same FSHy parents")
  void sameFshyParents() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Basic";
    Profile profileBar = new Profile("Bar");
    profileBar.parent = "Foo";
    Profile profileBaz = new Profile("Baz");
    profileBaz.parent = "Foo";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.profiles.put(profileBar.name, profileBar);
    doc.profiles.put(profileBaz.name, profileBaz);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(3, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Baz", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[0].url);
  }

  @Test
  @DisplayName("ProfileExporter should export profiles with deep FSHy parents")
  void deepFshyParents() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Basic";
    Profile profileBar = new Profile("Bar");
    profileBar.parent = "Foo";
    Profile profileBaz = new Profile("Baz");
    profileBaz.parent = "Bar";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.profiles.put(profileBar.name, profileBar);
    doc.profiles.put(profileBaz.name, profileBaz);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(3, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Baz", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[1].url);
  }

  @Test
  @DisplayName("ProfileExporter should export profiles with out-of-order FSHy parents")
  void outOfOrderFshyParents() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Bar";
    Profile profileBar = new Profile("Bar");
    profileBar.parent = "Baz";
    Profile profileBaz = new Profile("Baz");
    profileBaz.parent = "Basic";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.profiles.put(profileBar.name, profileBar);
    doc.profiles.put(profileBaz.name, profileBaz);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(3, exported.size());
    assertEquals("Baz", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Foo", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[1].url);
  }

  @Test
  @DisplayName("ProfileExporter should export a profile with an abstract profile parent")
  void abstractProfileParent() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Basic";
    CaretValueRule abstractRule = new CaretValueRule("");
    abstractRule.caretPath = "abstract";
    abstractRule.value = true;
    profileFoo.rules.add(abstractRule);
    Profile profileBar = new Profile("Bar");
    profileBar.parent = "Foo";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.profiles.put(profileBar.name, profileBar);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals(true, at(exported.get(0), "abstract"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals(false, at(exported.get(1), "abstract"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ProfileExporter should export a profile with a logical parent")
  void logicalParent() {
    Profile profile = new Profile("Foo");
    profile.parent = "ELTSSServiceModel";
    doc.profiles.put(profile.name, profile);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("logical", at(exported.get(0), "kind"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ProfileExporter should export profiles with deep logical parents")
  void deepLogicalParents() {
    Profile fooProfile = new Profile("Foo");
    fooProfile.parent = "ELTSSServiceModel";
    Profile barProfile = new Profile("Bar");
    barProfile.parent = "Foo";
    doc.profiles.put(fooProfile.name, fooProfile);
    doc.profiles.put(barProfile.name, barProfile);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("logical", at(exported.get(0), "kind"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("logical", at(exported.get(1), "kind"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ProfileExporter should export profiles with profile instance parents")
  void profileInstanceParents() {
    Instance parentProfileInstance = new Instance("ParentProfile");
    parentProfileInstance.instanceOf = "StructureDefinition";
    parentProfileInstance.usage = InstanceUsage.Definition;
    AssignmentRule parentName = new AssignmentRule("name");
    parentName.value = "ParentProfile";
    AssignmentRule parentStatus = new AssignmentRule("status");
    parentStatus.value = new FshCode("active");
    AssignmentRule parentKind = new AssignmentRule("kind");
    parentKind.value = new FshCode("resource");
    AssignmentRule parentAbstract = new AssignmentRule("abstract");
    parentAbstract.value = false;
    AssignmentRule parentType = new AssignmentRule("type");
    parentType.value = "Observation";
    AssignmentRule parentDerivation = new AssignmentRule("derivation");
    parentDerivation.value = new FshCode("constraint");
    AssignmentRule parentBaseDefinition = new AssignmentRule("baseDefinition");
    parentBaseDefinition.value = "http://hl7.org/fhir/StructureDefinition/Observation";
    AssignmentRule parentElementId = new AssignmentRule("snapshot.element[0].id");
    parentElementId.value = "Observation";
    AssignmentRule parentElementPath = new AssignmentRule("snapshot.element[0].path");
    parentElementPath.value = "Observation";
    parentProfileInstance.rules.addAll(List.of(parentName, parentStatus, parentKind, parentAbstract, parentType,
        parentDerivation, parentBaseDefinition, parentElementId, parentElementPath));
    doc.instances.put(parentProfileInstance.name, parentProfileInstance);

    Profile childProfile = new Profile("ChildProfile");
    childProfile.parent = "ParentProfile";
    doc.profiles.put(childProfile.name, childProfile);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertEquals("ChildProfile", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/ParentProfile",
        at(exported.get(0), "baseDefinition"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ProfileExporter should defer adding an instance to a profile as a contained resource")
  void deferContainedInstance() {
    Instance instance = new Instance("myResource");
    instance.instanceOf = "Observation";
    doc.instances.put(instance.name, instance);

    Profile profile = new Profile("ContainingProfile");
    profile.parent = "Basic";
    CaretValueRule caretValueRule = new CaretValueRule("");
    caretValueRule.caretPath = "contained";
    caretValueRule.value = "myResource";
    caretValueRule.isInstance = true;
    profile.rules.add(caretValueRule);
    doc.profiles.put(profile.name, profile);

    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "contained"));
    assertEquals(1, exporter.deferredCaretRules.size());
    assertEquals(1, exporter.deferredCaretRules.get(exported.get(0)).size());
    assertTrue(exporter.deferredCaretRules.get(exported.get(0))
        .contains(new DeferredCaretRule(caretValueRule, true, null)));
  }

  @Test
  @DisplayName("ProfileExporter should defer adding an instance with a numeric id to a profile as a contained resource")
  void deferContainedNumericId() {
    Instance instance = new Instance("1312");
    instance.instanceOf = "Observation";
    doc.instances.put(instance.name, instance);

    Profile profile = new Profile("ContainingProfile");
    profile.parent = "Basic";
    CaretValueRule caretValueRule = new CaretValueRule("");
    caretValueRule.caretPath = "contained";
    caretValueRule.value = 1312.0;
    caretValueRule.rawValue = "1312";
    profile.rules.add(caretValueRule);
    doc.profiles.put(profile.name, profile);

    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "contained"));
    assertEquals(1, exporter.deferredCaretRules.size());
    assertEquals(1, exporter.deferredCaretRules.get(exported.get(0)).size());
    // TS: toContainEqual({ rule, originalErr: expect.any(MismatchedTypeError), tryFish: true })
    assertTrue(exporter.deferredCaretRules.get(exported.get(0)).stream()
        .anyMatch(d -> d.rule() == caretValueRule && d.originalErr() instanceof MismatchedTypeError && d.tryFish()));
  }

  @Test
  @DisplayName("ProfileExporter should defer adding an instance with an id that resembles a boolean to a profile as a contained resource")
  void deferContainedBooleanId() {
    Instance instance = new Instance("true");
    instance.instanceOf = "Observation";
    doc.instances.put(instance.name, instance);

    Profile profile = new Profile("ContainingProfile");
    profile.parent = "Basic";
    CaretValueRule caretValueRule = new CaretValueRule("");
    caretValueRule.caretPath = "contained";
    caretValueRule.value = true;
    caretValueRule.rawValue = "true";
    profile.rules.add(caretValueRule);
    doc.profiles.put(profile.name, profile);

    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "contained"));
    assertEquals(1, exporter.deferredCaretRules.size());
    assertEquals(1, exporter.deferredCaretRules.get(exported.get(0)).size());
    // TS: toContainEqual({ rule, originalErr: expect.any(MismatchedTypeError), tryFish: true })
    assertTrue(exporter.deferredCaretRules.get(exported.get(0)).stream()
        .anyMatch(d -> d.rule() == caretValueRule && d.originalErr() instanceof MismatchedTypeError && d.tryFish()));
  }

  @Test
  @DisplayName("ProfileExporter should defer adding a binding to an inline ValueSet resource")
  void deferInlineValueSetBinding() {
    Instance instance = new Instance("MyValueSet");
    instance.instanceOf = "ValueSet";
    instance.usage = InstanceUsage.Inline;
    doc.instances.put(instance.name, instance);

    Profile profile = new Profile("ContainingProfile");
    profile.parent = "Basic";
    CaretValueRule caretValueRule = new CaretValueRule("");
    caretValueRule.caretPath = "contained";
    caretValueRule.value = "MyValueSet";
    caretValueRule.isInstance = true;
    BindingRule bindingRule = new BindingRule("code");
    bindingRule.valueSet = "MyValueSet";
    bindingRule.strength = "extensible";
    profile.rules.addAll(List.of(caretValueRule, bindingRule));
    doc.profiles.put(profile.name, profile);

    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "contained"));
    ElementDefinition codeElement = exported.get(0).findElement("Basic.code");
    assertNotEquals("#MyValueSet", at(codeElement, "binding.valueSet"));
    assertEquals(1, exporter.deferredCaretRules.size());
    assertEquals(1, exporter.deferredCaretRules.get(exported.get(0)).size());
    assertTrue(exporter.deferredCaretRules.get(exported.get(0))
        .contains(new DeferredCaretRule(caretValueRule, true, null)));
    assertEquals(1, exporter.knownBindingRules.size());
    assertEquals(1, exporter.knownBindingRules.get(exported.get(0)).size());
    // TS: toContainEqual({ rule: bindingRule, isInline: true }) (url is undefined for inline bindings)
    assertTrue(exporter.knownBindingRules.get(exported.get(0)).contains(new KnownBindingRule(bindingRule, true, null)));
  }

  @Test
  @DisplayName("ProfileExporter should allow a contained resource with a resourceType to be built from several caret rules")
  void containedFromSeveralCaretRules() {
    // Profile: ContainingProfile
    // Parent: Patient
    // * ^contained.resourceType = "Observation"
    // * ^contained.id = "my-observation"
    // * ^contained.status = #draft
    // * ^contained.code = #123
    // * ^contained.valueString = "contained observation"
    Profile profile = new Profile("ContainingProfile");
    profile.parent = "Patient";
    CaretValueRule containedResourceType = new CaretValueRule("");
    containedResourceType.caretPath = "contained.resourceType";
    containedResourceType.value = "Observation";
    CaretValueRule containedId = new CaretValueRule("");
    containedId.caretPath = "contained.id";
    containedId.value = "my-observation";
    CaretValueRule containedStatus = new CaretValueRule("");
    containedStatus.caretPath = "contained.status";
    containedStatus.value = new FshCode("draft");
    CaretValueRule containedCode = new CaretValueRule("");
    containedCode.caretPath = "contained.code";
    containedCode.value = new FshCode("123");
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    profile.rules.addAll(List.of(containedResourceType, containedId, containedStatus, containedCode, containedValue));
    doc.profiles.put(profile.name, profile);

    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "Observation",
          "id": "my-observation",
          "status": "draft",
          "code": {
            "coding": [
              {
                "code": "123"
              }
            ]
          },
          "valueString": "contained observation"
        }
        """, at(exported.get(0), "contained[0]"));
  }

  @Test
  @DisplayName("ProfileExporter should defer applying a caret rule that would be applied within a contained instance")
  void deferCaretRuleWithinContainedInstance() {
    // Instance: MyObservation
    // InstanceOf: Observation
    // Usage: #inline
    // * id = "my-observation"
    // * status = #draft
    // * code = #123
    Instance instance = new Instance("MyObservation");
    instance.instanceOf = "Observation";
    instance.usage = InstanceUsage.Inline;
    doc.instances.put(instance.name, instance);
    // Profile: ContainingProfile
    // Parent: Patient
    // * ^contained = MyObservation
    // * ^contained.valueString = "contained observation"
    Profile profile = new Profile("ContainingProfile");
    profile.parent = "Patient";
    CaretValueRule containedInstance = new CaretValueRule("");
    containedInstance.caretPath = "contained";
    containedInstance.value = "MyObservation";
    containedInstance.isInstance = true;
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    profile.rules.addAll(List.of(containedInstance, containedValue));
    doc.profiles.put(profile.name, profile);

    List<StructureDefinition> exported = exporter.export().profiles;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "contained"));
    assertEquals(1, exporter.deferredCaretRules.size());
    assertEquals(
        List.of(new DeferredCaretRule(containedInstance, true, null), new DeferredCaretRule(containedValue, false, null)),
        exporter.deferredCaretRules.get(exported.get(0)));
  }

  @Test
  @DisplayName("ProfileExporter should NOT export a profile of an R5 resource in an R4 project")
  void noR5ResourceProfileInR4() {
    // Although instances of ActorDefinition are allowed in R4, profiles of ActorDefinition are not!
    Profile adProfile = new Profile("ADProfile");
    adProfile.parent = "ActorDefinition";
    doc.profiles.put(adProfile.name, adProfile);
    List<StructureDefinition> exported = exporter.export().profiles;
    assertTrue(exported.isEmpty());
    assertEquals("Parent ActorDefinition not found for ADProfile", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ProfileExporter should throw a MismatchedBindingTypeError when a code property is bound to a code system")
  void mismatchedBindingType() {
    Profile profile = new Profile("TestProfile");
    profile.parent = "Patient";
    BindingRule bindingRule = new BindingRule("identifier.type");
    bindingRule.valueSet = "W3cProvenanceActivityType";
    bindingRule.strength = "required";
    profile.rules.add(bindingRule);
    doc.profiles.put(profile.name, profile);
    exporter.export();

    assertMatches("A ValueSet must be used.", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ProfileExporter should log an error when an inline extension is used")
  void inlineExtension() {
    Profile profile = new Profile("MyObservation");
    profile.parent = "Observation";
    ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("MyObservation.fsh")
        .withLocation(3, 8, 3, 25);
    containsRule.items.add(new ContainsRuleItem("SomeExtension"));
    profile.rules.add(containsRule);
    doc.profiles.put(profile.name, profile);
    exporter.export();

    assertMatches("File: MyObservation\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Inline extensions should only be defined in Extensions", "s", loggerSpy.getLastMessage("error"));
  }
}
