package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertContainsText;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetConceptComponentRule;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

// port of sushi/test/export/FHIRExporter.test.ts (SUSHI 3.21.1)
@DisplayName("FHIRExporter")
class FHIRExporterTest {
  static CaretValueRule caret(String caretPath, Object value, boolean isInstance) {
    CaretValueRule r = new CaretValueRule("");
    r.caretPath = caretPath;
    r.value = value;
    r.isInstance = isInstance;
    return r;
  }

  static CaretValueRule caretRaw(String caretPath, Object value, String rawValue) {
    CaretValueRule r = caret(caretPath, value, false);
    r.rawValue = rawValue;
    return r;
  }

  static AssignmentRule assign(String path, Object value) {
    AssignmentRule r = new AssignmentRule(path);
    r.value = value;
    return r;
  }

  static Instance instance(String name, String instanceOf, InstanceUsage usage) {
    Instance i = new Instance(name);
    i.instanceOf = instanceOf;
    if (usage != null) {
      i.usage = usage;
    }
    return i;
  }

  static BindingRule binding(String path, String valueSet, String strength) {
    BindingRule r = new BindingRule(path);
    r.valueSet = valueSet;
    r.strength = strength;
    return r;
  }

  @Test
  @DisplayName("FHIRExporter should output empty results with empty input")
  void emptyInput() {
    Assumptions.assumeTrue(TestFHIRDefinitions.tsFixturesAvailable(), "TS SUSHI checkout not found");
    TestFisher.assumeTankImplemented();
    FSHTank input = new FSHTank(new ArrayList<>(), minimalConfig());
    Package result = ExportFHIR.exportFHIR(input, getTestFHIRDefinitions());
    // toEqual(new Package({...minimal config...})): empty lists and maps, and an equal config
    for (List<?> l : List.of(result.profiles, result.extensions, result.logicals, result.resources,
        result.instances, result.valueSets, result.codeSystems)) {
      assertTrue(l.isEmpty());
    }
    assertTrue(result.fshMap.isEmpty());
    Configuration c = result.config;
    assertEquals("sushi-config.yaml", c.filePath);
    assertEquals("fhir.us.minimal", c.id);
    assertEquals("1.0.0", c.version);
    assertEquals("http://hl7.org/fhir/us/minimal", c.canonical);
    assertEquals("MinimalIG", c.name);
    assertEquals("draft", c.status);
    assertEquals(List.of("4.0.1"), c.fhirVersion);
  }

  @Nested
  @DisplayName("#containedResources")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ContainedResources {
    TestFHIRDefinitions defs;
    FSHDocument doc;
    FHIRExporter exporter;

    @BeforeAll
    void setUpAll() {
      Assumptions.assumeTrue(TestFHIRDefinitions.tsFixturesAvailable(), "TS SUSHI checkout not found");
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
    }

    @BeforeEach
    void setUp() {
      TestFisher.assumeTankImplemented();
      loggerSpy.reset();
      doc = new FSHDocument("fileName");
      FSHTank input = new FSHTank(new ArrayList<>(List.of(doc)), minimalConfig());
      Package pkg = new Package(input.config);
      TestFisher fisher = new TestFisher(input, defs, pkg);
      exporter = new FHIRExporter(input, pkg, fisher);
    }

    Profile profile(String name, String parent, Object... rules) {
      Profile p = new Profile(name);
      p.parent = parent;
      for (Object r : rules) {
        p.rules.add((org.hl7.fhir.sushi.fshtypes.rules.Rule) r);
      }
      doc.profiles.put(p.name, p);
      return p;
    }

    void addInstance(Instance i, AssignmentRule... rules) {
      i.rules.addAll(List.of(rules));
      doc.instances.put(i.name, i);
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to contain a defined FHIR resource")
    void t01() {
      profile("ContainingProfile", "Basic", caret("contained", "allergyintolerance-clinical", true));

      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      assertEquals(1, result.profiles.get(0).getContained().size());
      Object containedResource = result.profiles.get(0).getContained().get(0);
      Object expected = defs.findResourceJSONs("*", FindResourceInfoOptions.ofType("ValueSet")).stream()
          .filter(vs -> "allergyintolerance-clinical".equals(at(vs, "id"))).findFirst().orElse(null);
      assertJsEqual(expected, containedResource);
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to contain a FSH resource")
    void t02() {
      addInstance(instance("myObservation", "Observation", null));
      profile("ContainingProfile", "Basic", caret("contained", "myObservation", true));

      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      assertEquals(1, result.profiles.get(0).getContained().size());
      assertJsonEqual("{\"resourceType\": \"Observation\", \"id\": \"myObservation\"}",
          result.profiles.get(0).getContained().get(0));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to contain a FSH resource with a numeric id")
    void t03() {
      addInstance(instance("010203", "Observation", null));
      profile("ContainingProfile", "Basic", caretRaw("contained", 10203.0, "010203"));

      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      assertEquals(1, result.profiles.get(0).getContained().size());
      assertJsonEqual("{\"resourceType\": \"Observation\", \"id\": \"010203\"}",
          result.profiles.get(0).getContained().get(0));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to contain a FSH resource with an id that resembles a boolean")
    void t04() {
      addInstance(instance("false", "Observation", null));
      profile("ContainingProfile", "Basic", caretRaw("contained", false, "false"));

      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      assertEquals(1, result.profiles.get(0).getContained().size());
      assertJsonEqual("{\"resourceType\": \"Observation\", \"id\": \"false\"}",
          result.profiles.get(0).getContained().get(0));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to contain multiple FSH resources")
    void t05() {
      addInstance(instance("010203", "Observation", null));
      addInstance(instance("CleanSocks", "Observation", null));
      addInstance(instance("456", "Location", null));
      profile("ContainingProfile", "Basic", caretRaw("contained", 10203.0, "010203"),
          caret("contained[1]", "CleanSocks", true), caretRaw("contained[2]", 456.0, "456"));

      Package result = exporter.export();
      assertEquals(3, result.profiles.get(0).getContained().size());
      assertJsonEqual("""
          [
            {"resourceType": "Observation", "id": "010203"},
            {"resourceType": "Observation", "id": "CleanSocks"},
            {"resourceType": "Location", "id": "456"}
          ]""", result.profiles.get(0).getContained());
    }

    Instance myObservation() {
      // Instance: MyObservation
      // InstanceOf: Observation
      // Usage: #inline
      // * id = "my-observation"
      // * status = #draft
      // * code = #123
      Instance instance = instance("MyObservation", "Observation", InstanceUsage.Inline);
      addInstance(instance, assign("id", "my-observation"), assign("status", new FshCode("draft")),
          assign("code", new FshCode("123")));
      return instance;
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to contain a resource and to apply caret rules within the contained resource")
    void t06() {
      myObservation();
      // Profile: ContainingProfile
      // Parent: Patient
      // * ^contained = MyObservation
      // * ^contained.valueString = "contained observation"
      // * ^contained.category = #exam
      profile("ContainingProfile", "Patient", caret("contained", "MyObservation", true),
          caret("contained.valueString", "contained observation", false),
          caret("contained.category", new FshCode("exam"), false));

      Package result = exporter.export();
      assertJsonEqual("""
          [
            {
              "resourceType": "Observation",
              "id": "my-observation",
              "status": "draft",
              "code": {"coding": [{"code": "123"}]},
              "valueString": "contained observation",
              "category": [{"coding": [{"code": "exam"}]}]
            }
          ]""", result.profiles.get(0).getContained());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should log an error when a deferred rule assigns something of the wrong type")
    void t07() {
      myObservation();
      // Profile: ContainingProfile
      // Parent: Patient
      // * ^contained = MyObservation
      // * ^contained.interpretation = "contained observation"
      CaretValueRule containedValue = caret("contained.interpretation", "contained observation", false)
          .<CaretValueRule>withFile("Contained.fsh").withLocation(15, 3, 15, 33);
      profile("ContainingProfile", "Patient", caret("contained", "MyObservation", true), containedValue);
      Package result = exporter.export();
      assertJsonEqual("""
          [
            {
              "resourceType": "Observation",
              "id": "my-observation",
              "status": "draft",
              "code": {"coding": [{"code": "123"}]}
            }
          ]""", result.profiles.get(0).getContained());
      assertContainsText("Cannot assign string value: contained observation. Value does not match element type: "
          + "CodeableConcept", loggerSpy.getLastMessage("error"));
      assertMatches("File: Contained\\.fsh.*Line: 15\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should not get confused when there are contained resources of different types")
    void t08() {
      myObservation();
      // Instance: MyPatient
      // InstanceOf: Patient
      // Usage: #inline
      // * id = "my-patient"
      // * name.given = "Marisa"
      addInstance(instance("MyPatient", "Patient", InstanceUsage.Inline), assign("id", "my-patient"),
          assign("name.given", "Marisa"));
      // Profile: ContainingProfile
      // Parent: Patient
      // * ^contained = MyObservation
      // * ^contained[1] = MyPatient
      // * ^contained.valueString = "contained observation"
      // * ^contained[1].name.family = "Kirisame"
      profile("ContainingProfile", "Patient", caret("contained", "MyObservation", true),
          caret("contained[1]", "MyPatient", true), caret("contained.valueString", "contained observation", false),
          caret("contained[1].name.family", "Kirisame", false));

      Package result = exporter.export();
      List<Object> contained = result.profiles.get(0).getContained();
      assertEquals(2, contained.size());
      assertJsonEqual("""
          {
            "resourceType": "Observation",
            "id": "my-observation",
            "status": "draft",
            "code": {"coding": [{"code": "123"}]},
            "valueString": "contained observation"
          }""", contained.get(0));
      assertJsonEqual("""
          {"resourceType": "Patient", "id": "my-patient", "name": [{"given": ["Marisa"], "family": "Kirisame"}]}""",
          contained.get(1));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to contain a profiled resource and to apply a caret rule within the contained resource")
    void t09() {
      // Instance: some-patient
      // InstanceOf: gendered-patient
      // Usage: #inline
      // * gender = #unknown
      addInstance(instance("some-patient", "gendered-patient", InstanceUsage.Inline),
          assign("gender", new FshCode("unknown")));
      // Profile: ContainingProfile
      // Parent: Patient
      // * ^contained = some-patient
      // * ^contained.name.given = "mint"
      profile("ContainingProfile", "Patient", caret("contained", "some-patient", true),
          caret("contained.name.given", "mint", false));

      Package result = exporter.export();
      assertJsonEqual("""
          [
            {
              "resourceType": "Patient",
              "meta": {"profile": ["http://example.org/impose/StructureDefinition/gendered-patient"]},
              "id": "some-patient",
              "gender": "unknown",
              "name": [{"given": ["mint"]}]
            }
          ]""", result.profiles.get(0).getContained());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    void assertCodeBinding(Package result, String valueSet) {
      ElementDefinition codeElement = result.profiles.get(0).findElement("Basic.code");
      assertEquals("extensible", at(codeElement, "binding.strength"));
      assertEquals(valueSet, at(codeElement, "binding.valueSet"));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to bind an element to a contained ValueSet using a relative reference")
    void t10() {
      FshValueSet valueSet = new FshValueSet("MyValueSet");
      valueSet.setId("my-value-set");
      doc.valueSets.put(valueSet.name, valueSet);
      profile("ContainingProfile", "Basic", caret("contained", "MyValueSet", true),
          binding("code", "MyValueSet", "extensible"));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertJsonEqual("""
          [
            {
              "resourceType": "ValueSet",
              "id": "my-value-set",
              "name": "MyValueSet",
              "url": "http://hl7.org/fhir/us/minimal/ValueSet/my-value-set",
              "status": "draft"
            }
          ]""", result.profiles.get(0).getContained());
      assertCodeBinding(result, "#my-value-set");
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to bind an element to a contained inline instance of ValueSet using a relative reference")
    void t11() {
      addInstance(instance("MyValueSet", "ValueSet", InstanceUsage.Inline));
      profile("ContainingProfile", "Basic", caret("contained", "MyValueSet", true),
          binding("code", "MyValueSet", "extensible"));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertJsonEqual("[{\"resourceType\": \"ValueSet\", \"id\": \"MyValueSet\"}]",
          result.profiles.get(0).getContained());
      assertCodeBinding(result, "#MyValueSet");
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to bind an element to a contained inline instance of ValueSet with name set by a rule, using a relative reference")
    void t12() {
      addInstance(instance("my-value-set", "ValueSet", InstanceUsage.Inline), assign("name", "MyValueSet"));
      profile("ContainingProfile", "Basic", caret("contained", "my-value-set", true),
          binding("code", "MyValueSet", "extensible"));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertJsonEqual("[{\"resourceType\": \"ValueSet\", \"id\": \"my-value-set\", \"name\": \"MyValueSet\"}]",
          result.profiles.get(0).getContained());
      assertCodeBinding(result, "#my-value-set");
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to bind an element to a contained inline instance of ValueSet with url set by a rule, using a relative reference")
    void t13() {
      addInstance(instance("MyValueSet", "ValueSet", InstanceUsage.Inline),
          assign("url", "http://hl7.org/fhir/us/custom/ValueSet/MyValueSet"));
      profile("ContainingProfile", "Basic", caret("contained", "MyValueSet", true),
          binding("code", "http://hl7.org/fhir/us/custom/ValueSet/MyValueSet", "extensible"));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertJsonEqual("""
          [{"resourceType": "ValueSet", "id": "MyValueSet",
            "url": "http://hl7.org/fhir/us/custom/ValueSet/MyValueSet"}]""", result.profiles.get(0).getContained());
      assertCodeBinding(result, "#MyValueSet");
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to bind an element to a contained definitional instance of ValueSet using a relative reference")
    void t14() {
      addInstance(instance("MyValueSet", "ValueSet", InstanceUsage.Definition));
      profile("ContainingProfile", "Basic", caret("contained", "MyValueSet", true),
          binding("code", "MyValueSet", "extensible"));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertJsonEqual("""
          [{"resourceType": "ValueSet", "id": "MyValueSet",
            "url": "http://hl7.org/fhir/us/minimal/ValueSet/MyValueSet"}]""", result.profiles.get(0).getContained());
      assertCodeBinding(result, "#MyValueSet");
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to bind an element by name to a contained definitional instance of ValueSet with a name set by a rule using a relative reference")
    void t15() {
      addInstance(instance("MyValueSet", "ValueSet", InstanceUsage.Definition), assign("name", "MyVS"));
      profile("ContainingProfile", "Basic", caret("contained", "MyValueSet", true),
          binding("code", "MyVS", "extensible"));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertJsonEqual("""
          [{"resourceType": "ValueSet", "id": "MyValueSet",
            "url": "http://hl7.org/fhir/us/minimal/ValueSet/MyValueSet", "name": "MyVS"}]""",
          result.profiles.get(0).getContained());
      assertCodeBinding(result, "#MyValueSet");
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should allow a profile to bind an element to a contained ValueSet using a relative reference when the rule includes a version")
    void t16() {
      FshValueSet valueSet = new FshValueSet("MyValueSet");
      valueSet.setId("my-value-set");
      valueSet.rules.add(caret("version", "1.2.8", false));
      doc.valueSets.put(valueSet.name, valueSet);
      profile("ContainingProfile", "Basic", caret("contained", "MyValueSet|1.2.8", true),
          binding("code", "MyValueSet", "extensible"));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertJsonEqual("""
          [
            {
              "resourceType": "ValueSet",
              "id": "my-value-set",
              "name": "MyValueSet",
              "url": "http://hl7.org/fhir/us/minimal/ValueSet/my-value-set",
              "status": "draft",
              "version": "1.2.8"
            }
          ]""", result.profiles.get(0).getContained());
      assertCodeBinding(result, "#my-value-set");
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should log an error when attempting to bind an element to an inline ValueSet instance that is not contained in the profile")
    void t17() {
      addInstance(instance("MyValueSet", "ValueSet", InstanceUsage.Inline));
      BindingRule bindingRule = binding("code", "MyValueSet", "extensible").<BindingRule>withFile("Profile.fsh")
          .withLocation(8, 3, 8, 29);
      profile("ContainingProfile", "Basic", bindingRule);

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertNull(result.profiles.get(0).getContained());
      ElementDefinition codeElement = result.profiles.get(0).findElement("Basic.code");
      assertNotEquals("extensible", at(codeElement, "binding.strength"));
      assertNotEquals("#MyValueSet", at(codeElement, "binding.valueSet"));
      assertMatches("Can not bind code to ValueSet MyValueSet: this ValueSet is an inline instance, but it is not "
          + "present in the list of contained resources.*File: Profile\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should log an error when a profile tries to contain an instance that is not a resource")
    void t18() {
      addInstance(instance("MyCodeable", "CodeableConcept", InstanceUsage.Inline));
      profile("ContainingProfile", "Patient", caret("contained[0]", "MyCodeable", true));

      Package result = exporter.export();
      assertEquals(1, result.profiles.size());
      assertNull(result.profiles.get(0).getContained());
      assertMatches("Cannot assign CodeableConcept value: MyCodeable", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should log an error when a profile tries to contain a resource that does not exist")
    void t19() {
      profile("ContainingProfile", "Basic", caret("contained", "oops-no-resource", true));

      exporter.export();
      assertMatches("Could not find a resource named oops-no-resource", "s", loggerSpy.getLastMessage("error"));
    }

    Instance myContact() {
      // Instance: MyContact
      // InstanceOf: ContactDetail
      // Usage: #inline
      // name = "Bearington"
      Instance instance = instance("MyContact", "ContactDetail", InstanceUsage.Inline);
      addInstance(instance, assign("name", "Bearington"));
      return instance;
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should let a profile assign an Inline instance that is not a resource")
    void t20() {
      // Profile: MyObservation
      // Parent: Observation
      // ^contact = MyContact
      profile("MyObservation", "Observation", caret("contact", "MyContact", true));
      myContact();

      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      List<?> contact = (List<?>) at(result.profiles.get(0), "contact");
      assertEquals(1, contact.size());
      assertJsonEqual("{\"name\": \"Bearington\"}", contact.get(0));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should let a profile assign and modify an Inline instance that is not a resource")
    void t21() {
      // Profile: MyObservation
      // Parent: Observation
      // ^contact = MyContact
      // ^contact.telecom.value = "bearington@bear.zoo"
      profile("MyObservation", "Observation", caret("contact", "MyContact", true),
          caret("contact.telecom.value", "bearington@bear.zoo", false));
      myContact();

      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      List<?> contact = (List<?>) at(result.profiles.get(0), "contact");
      assertEquals(1, contact.size());
      assertJsonEqual("{\"name\": \"Bearington\", \"telecom\": [{\"value\": \"bearington@bear.zoo\"}]}",
          contact.get(0));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should export a value set that includes a component from a contained FSH code system and add the valueset-system extension")
    void t22() {
      // CodeSystem: FoodCS
      // Id: food
      FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
      foodCS.setId("food");
      doc.codeSystems.put(foodCS.name, foodCS);
      // ValueSet: DinnerVS
      // * ^contained[0] = FoodCS
      // * include codes from system food
      FshValueSet valueSet = new FshValueSet("DinnerVS");
      ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
      component.from.system = "FoodCS";
      valueSet.rules.add(caret("contained[0]", "FoodCS", true));
      valueSet.rules.add(component);
      doc.valueSets.put(valueSet.name, valueSet);

      Package exported = exporter.export();
      assertEquals(1, exported.valueSets.size());
      assertJsonEqual("""
          {
            "resourceType": "ValueSet",
            "name": "DinnerVS",
            "id": "DinnerVS",
            "status": "draft",
            "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
            "contained": [
              {
                "content": "complete",
                "id": "food",
                "name": "FoodCS",
                "resourceType": "CodeSystem",
                "status": "draft",
                "url": "http://hl7.org/fhir/us/minimal/CodeSystem/food"
              }
            ],
            "compose": {
              "include": [
                {
                  "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                  "_system": {
                    "extension": [
                      {"url": "http://hl7.org/fhir/StructureDefinition/valueset-system", "valueCanonical": "#food"}
                    ]
                  }
                }
              ]
            }
          }""", exported.valueSets.get(0));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should log a message when trying to assign a value that is numeric and refers to an Instance, but both types are wrong")
    void t23() {
      // Profile: MyObservation
      // Parent: Observation
      // ^identifier = 1234
      CaretValueRule identifierRule = caretRaw("identifier", 1234.0, "1234").<CaretValueRule>withFile("Profiles.fsh")
          .withLocation(10, 5, 10, 29);
      profile("MyObservation", "Observation", identifierRule);
      // Instance: 1234
      // InstanceOf: ContactDetail
      // Usage: #inline
      // name = "Bearington"
      addInstance(instance("1234", "ContactDetail", InstanceUsage.Inline), assign("name", "Bearington"));

      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      assertMatches("Cannot assign number value: 1234\\. Value does not match element type: Identifier.*File: "
          + "Profiles\\.fsh.*Line: 10\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("FHIRExporter #containedResources should log a message and not change the URL when trying to assign an instance to a URL and the instance is not the correct type")
    void t24() {
      // this represents the case where the author does not quote the url value, such as:
      // * ^url = http://example.org/some/url
      CaretValueRule rule = caret("url", "http://example.org/some/url", true).<CaretValueRule>withFile(
          "UnquotedUrl.fsh").withLocation(4, 3, 4, 12);
      profile("MyObservation", "Observation", rule);
      Package result = exporter.export();

      assertEquals(1, result.profiles.size());
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyObservation",
          result.profiles.get(0).getUrl());
      assertContainsText("Could not find a resource named http://example.org/some/url",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: UnquotedUrl\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
    }
  }
}
