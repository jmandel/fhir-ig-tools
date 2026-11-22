package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.InsertRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R4' > '#CaretValueRule', '#ObeysRule', '#Extension preprocessing' (lines 8661-10300)
@DisplayName("StructureDefinitionExporter R4")
class StructureDefinitionExporterR4FTest extends SdeTestBase {

  private static final String BEST_PRACTICE_URL =
      "http://hl7.org/fhir/StructureDefinition/elementdefinition-bestpractice";
  private static final String BEST_PRACTICE_PATH = "extension[" + BEST_PRACTICE_URL + "].valueBoolean";

  private static List<OnlyRuleType> types(OnlyRuleType... t) {
    return new ArrayList<>(Arrays.asList(t));
  }

  private static List<Rule> rules(Rule... r) {
    return new ArrayList<>(Arrays.asList(r));
  }

  private static List<ContainsRuleItem> items(ContainsRuleItem... i) {
    return new ArrayList<>(Arrays.asList(i));
  }

  private static CaretValueRule caret(String path, String caretPath, Object value) {
    CaretValueRule rule = new CaretValueRule(path);
    rule.caretPath = caretPath;
    rule.value = value;
    return rule;
  }

  private static CardRule card(String path, double min, String max) {
    CardRule rule = new CardRule(path);
    rule.min = min;
    rule.max = max;
    return rule;
  }

  private static AssignmentRule assign(String path, Object value) {
    AssignmentRule rule = new AssignmentRule(path);
    rule.value = value;
    return rule;
  }

  private static Invariant invariant(String name, String description, String severity) {
    Invariant invariant = new Invariant(name);
    invariant.description = description;
    invariant.severity = new FshCode(severity);
    return invariant;
  }

  private static ObeysRule obeys(String path, String invariant) {
    ObeysRule rule = new ObeysRule(path);
    rule.invariant = invariant;
    return rule;
  }

  /** TS {@code sd.elements.find(e => e.id === id)}. */
  private static ElementDefinition elementById(StructureDefinition sd, String id) {
    return sd.elements.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
  }

  /** TS {@code list.filter(e => e.url === url)} over a JS array (e.g. sd.extension). */
  private static List<Object> withUrl(Object list, String url) {
    List<Object> result = new ArrayList<>();
    if (list instanceof List<?> l) {
      for (Object e : l) {
        if (url.equals(at(e, "url"))) result.add(e);
      }
    }
    return result;
  }

  /** TS {@code list.find(e => e.url === url)}. */
  private static Object findUrl(Object list, String url) {
    List<Object> found = withUrl(list, url);
    return found.isEmpty() ? null : found.get(0);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> jsonList(String text) {
    return (List<Object>) json(text);
  }

  private static int size(Object list) {
    return ((List<?>) list).size();
  }

  /** TS testhelpers assertCardRule. */
  private static void assertCardRule(Rule rule, String path, double min, String max) {
    CardRule cardRule = assertInstanceOf(CardRule.class, rule);
    assertEquals(path, cardRule.path);
    assertEquals(Double.valueOf(min), cardRule.min);
    assertEquals(max, cardRule.max);
  }

  /** TS testhelpers assertContainsRule (string items only). */
  private static void assertContainsRule(Rule rule, String path, String... itemNames) {
    ContainsRule containsRule = assertInstanceOf(ContainsRule.class, rule);
    assertEquals(path, containsRule.path);
    List<ContainsRuleItem> expected = new ArrayList<>();
    for (String n : itemNames) expected.add(new ContainsRuleItem(n));
    assertEquals(expected, containsRule.items);
  }

  private static void assertCard(ElementDefinition ed, int min, String max) {
    assertJsEqual(min, at(ed, "min"));
    assertEquals(max, at(ed, "max"));
  }

  @Nested
  @DisplayName("#CaretValueRule")
  class CaretValueRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on an element with a path")
    void elementWithPath() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CaretValueRule rule = caret("status", "short", "foo");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition status = sd.findElement("Observation.status");
      assertEquals("foo", at(status, "short"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should not apply an invalid CaretValueRule on an element with a path")
    void invalidElementWithPath() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CaretValueRule rule = new CaretValueRule("status").<CaretValueRule>withFile("InvalidValue.fsh")
          .withLocation(6, 3, 6, 12);
      rule.caretPath = "short";
      rule.value = true;
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition status = sd.findElement("Observation.status");
      ElementDefinition baseStatus = baseStructDef.findElement("Observation.status");

      assertEquals(at(baseStatus, "short"), at(status, "short"));
      assertMatches("File: InvalidValue\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on the parent element")
    void parentElement() {
      // Profile: ShortObservation
      // Parent: Observation
      // * . ^ short = "This one is not so tall."
      Profile profile = new Profile("ShortObservation");
      profile.parent = "Observation";

      CaretValueRule rule = caret(".", "short", "This one is not so tall.");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition observation = sd.findElement("Observation");
      assertEquals("This one is not so tall.", at(observation, "short"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on an element without a path")
    void elementWithoutPath() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CaretValueRule rule = caret("", "description", "foo");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      assertEquals("foo", at(sd, "description"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on the child of a primitive element without a path")
    void childOfPrimitiveWithoutPath() {
      Profile profile = new Profile("SpecialUrlId");
      profile.parent = "Observation";

      CaretValueRule rule = caret("", "url.id", "my-id");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      assertJsonEqual("""
          { "id": "my-id" }""", at(sd, "_url"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on an extension of a primitive element without a path")
    void extensionOfPrimitiveWithoutPath() {
      // Extension: MyBooleanExtension
      // * value[x] only boolean
      Extension extension = new Extension("MyBooleanExtension");
      OnlyRule onlyBoolean = new OnlyRule("value[x]");
      onlyBoolean.types.add(new OnlyRuleType("boolean"));
      extension.rules.add(onlyBoolean);
      doc.extensions.put(extension.name, extension);
      // Profile: ExtensionOnPublisher
      // Parent: Patient
      // * ^publisher.extension[MyBooleanExtension].valueBoolean = true
      Profile profile = new Profile("ExtensionOnPublisher");
      profile.parent = "Patient";
      CaretValueRule rule = caret("", "publisher.extension[MyBooleanExtension].valueBoolean", true);
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      assertJsonEqual("""
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/MyBooleanExtension",
                "valueBoolean": true
              }
            ]
          }""", at(sd, "_publisher"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on an extension on ElementDefinition")
    void extensionOnElementDefinition() {
      // Extension: MyBooleanExtension
      // * value[x] only boolean
      Extension extension = new Extension("MyBooleanExtension");
      OnlyRule onlyBoolean = new OnlyRule("value[x]");
      onlyBoolean.types.add(new OnlyRuleType("boolean"));
      extension.rules.add(onlyBoolean);
      doc.extensions.put(extension.name, extension);
      // Profile: ExtensionOnName
      // Parent: Patient
      // * name ^extension[MyBooleanExtension].valueBoolean = true
      Profile profile = new Profile("ExtensionOnName");
      profile.parent = "Patient";
      CaretValueRule rule = caret("name", "extension[MyBooleanExtension].valueBoolean", true);
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition name = sd.findElement("Patient.name");
      assertJsonEqual("""
          [
            {
              "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/MyBooleanExtension",
              "valueBoolean": true
            }
          ]""", at(name, "extension"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on an extension on ElementDefinition even when the extension references an allowed R5 resource in an R4 IG")
    void extensionOnElementDefinitionWithR5Resource() {
      // This one caused some problems in manual testing of special R5 resources in R4 IGs

      // Instance: AD1
      // InstanceOf: ActorDefinition
      // Usage: #definition
      // * status = #active
      // * type = #server
      //
      // Profile: ExtensionOnName
      // Parent: Patient
      // * name ^extension[http://hl7.org/fhir/tools/StructureDefinition/obligation].extension[actor].valueCanonical = Canonical(AD1)
      // * name ^extension[http://hl7.org/fhir/tools/StructureDefinition/obligation].extension[code].valueCode = #SHALL

      Instance ad1 = new Instance("AD1");
      ad1.instanceOf = "ActorDefinition";
      ad1.usage = InstanceUsage.Definition;
      AssignmentRule statusRule = assign("status", new FshCode("active"));
      AssignmentRule typeRule = assign("type", new FshCode("server"));
      ad1.rules.addAll(List.of(statusRule, typeRule));
      doc.instances.put("AD1", ad1);

      Profile profile = new Profile("ExtensionOnName");
      profile.parent = "Patient";
      CaretValueRule actorRule = caret("name",
          "extension[http://hl7.org/fhir/tools/StructureDefinition/obligation].extension[actor].valueCanonical",
          new FshCanonical("AD1"));
      CaretValueRule codeRule = caret("name",
          "extension[http://hl7.org/fhir/tools/StructureDefinition/obligation].extension[code].valueCode",
          new FshCode("SHALL"));
      profile.rules.addAll(List.of(actorRule, codeRule));
      doc.profiles.put("ExtensionOnName", profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition name = sd.findElement("Patient.name");
      assertJsonEqual("""
          [
            {
              "url": "http://hl7.org/fhir/tools/StructureDefinition/obligation",
              "extension": [
                { "url": "actor", "valueCanonical": "http://hl7.org/fhir/us/minimal/ActorDefinition/AD1" },
                { "url": "code", "valueCode": "SHALL" }
              ]
            }
          ]""", at(name, "extension"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should not apply an invalid CaretValueRule on an element without a path")
    void invalidElementWithoutPath() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CaretValueRule rule = new CaretValueRule("").<CaretValueRule>withFile("InvalidValue.fsh")
          .withLocation(6, 3, 6, 12);
      rule.caretPath = "description";
      rule.value = new FshCode("1234");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      assertNull(at(sd, "description"));
      assertMatches("File: InvalidValue\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CaretValueRule on an extension element without a path")
    void extensionElementWithoutPath() {
      // Extension: SpecialExtension
      Extension extension = new Extension("SpecialExtension");
      doc.extensions.put(extension.name, extension);
      // Profile: HasSpecialExtension
      // Parent: Observation
      // * ^extension[SpecialExtension].valueString = "This is the special extension on the structure definition."
      Profile profile = new Profile("HasSpecialExtension");
      profile.parent = "Observation";
      CaretValueRule caretValueRule = caret("", "extension[SpecialExtension].valueString",
          "This is the special extension on the structure definition.");
      profile.rules.add(caretValueRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object extensionElement = findUrl(at(sd, "extension"),
          "http://hl7.org/fhir/us/minimal/StructureDefinition/SpecialExtension");
      assertNotNull(extensionElement);
      assertJsonEqual("""
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SpecialExtension",
            "valueString": "This is the special extension on the structure definition."
          }""", extensionElement);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a Reference CaretValueRule on an SD and replace the Reference")
    void referenceOnSd() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      Instance instance = new Instance("Bar");
      instance.setId("bar-id");
      instance.instanceOf = "Organization";
      doc.instances.put(instance.name, instance);

      CaretValueRule rule = caret("", "identifier[0].assigner", new FshReference("Bar"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      assertJsonEqual("""
          { "reference": "Organization/bar-id" }""", at(sd, "identifier[0].assigner"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a Reference CaretValueRule on an ED and replace the Reference")
    void referenceOnEd() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      Instance instance = new Instance("Bar");
      instance.setId("bar-id");
      instance.instanceOf = "Organization";
      doc.instances.put(instance.name, instance);

      CaretValueRule rule = caret("subject", "patternReference", new FshReference("Bar"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition ed = elementById(sd, "Observation.subject");

      assertJsonEqual("""
          { "reference": "Organization/bar-id" }""", at(ed, "patternReference"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CodeSystem CaretValueRule on an SD and replace the CodeSystem")
    void codeSystemOnSd() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      FshCodeSystem cs = new FshCodeSystem("Bar");
      cs.setId("bar-id");
      doc.codeSystems.put(cs.name, cs);

      CaretValueRule rule = caret("", "jurisdiction", new FshCode("foo", "Bar"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      assertJsonEqual("""
          {
            "code": "foo",
            "system": "http://hl7.org/fhir/us/minimal/CodeSystem/bar-id"
          }""", at(sd, "jurisdiction[0].coding[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply a CodeSystem CaretValueRule on an ED and replace the Reference")
    void codeSystemOnEd() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      FshCodeSystem cs = new FshCodeSystem("Bar");
      cs.setId("bar-id");
      doc.codeSystems.put(cs.name, cs);

      CaretValueRule rule = caret("subject", "code", new FshCode("foo", "Bar"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition ed = elementById(sd, "Observation.subject");

      assertJsonEqual("""
          {
            "code": "foo",
            "system": "http://hl7.org/fhir/us/minimal/CodeSystem/bar-id"
          }""", at(ed, "code[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should identify existing extensions by URL when applying a CaretValueRule on a StructureDefintiion")
    void existingExtensionsByUrl() {
      Profile parentProfile = new Profile("Foo");
      parentProfile.parent = "Observation";

      CaretValueRule parentRule = caret("", "extension[mothersMaidenName].valueString", "ParentName");
      parentProfile.rules.add(parentRule);

      Profile childProfile = new Profile("Bar");
      childProfile.parent = "Foo";

      CaretValueRule childRule = caret("", "extension[mothersMaidenName].valueString", "ChildName");
      childProfile.rules.add(childRule);

      // Profile: Foo
      // Parent: Observation
      // * ^extension[mothersMaidenName].valueString = "ParentName"
      //
      // Profile: Bar
      // Parent: Foo
      // * ^extension[mothersMaidenName].valueString = "ChildName"
      exporter.exportStructDef(parentProfile);
      exporter.exportStructDef(childProfile);

      StructureDefinition sd = pkg.profiles.get(1);

      String mmnUrl = "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName";
      // We should only find one copy of the extension
      assertEquals(1, withUrl(at(sd, "extension"), mmnUrl).size());
      // And it should have the correct value
      assertJsonEqual("""
          {
            "url": "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName",
            "valueString": "ChildName"
          }""", findUrl(at(sd, "extension"), mmnUrl));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply CaretValueRules on the targetProfile of a type")
    void targetProfileOfType() {
      // Alias: $typeMS = http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support
      doc.aliases.put("$typeMS", "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support");
      // Profile: MyDiagnosticReport
      // Parent: DiagnosticReport
      // * result only Reference(observation-bodyheight or observation-bodyweight)
      //   * ^type[0].targetProfile[0].extension[$typeMS].valueBoolean = true
      //   * ^type[0].targetProfile[1].extension[$typeMS].valueBoolean = true
      Profile profile = new Profile("MyDiagnosticReport");
      profile.parent = "DiagnosticReport";
      OnlyRule resultOnly = new OnlyRule("result");
      resultOnly.types = types(OnlyRuleType.reference("observation-bodyheight"),
          OnlyRuleType.reference("observation-bodyweight"));
      CaretValueRule firstExtension = caret("result",
          "type[0].targetProfile[0].extension[$typeMS].valueBoolean", true);
      CaretValueRule secondExtension = caret("result",
          "type[0].targetProfile[1].extension[$typeMS].valueBoolean", true);
      profile.rules.addAll(rules(resultOnly, firstExtension, secondExtension));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition ed = elementById(sd, "DiagnosticReport.result");
      assertEquals(1, ed.getType().size());
      ElementDefinitionType expectedType = new ElementDefinitionType("Reference").withTargetProfiles(
          "http://hl7.org/fhir/StructureDefinition/bodyheight",
          "http://hl7.org/fhir/StructureDefinition/bodyweight");
      expectedType.set_targetProfile(jsonList("""
          [
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support",
                  "valueBoolean": true
                }
              ]
            },
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support",
                  "valueBoolean": true
                }
              ]
            }
          ]"""));
      assertJsEqual(expectedType, ed.getType().get(0));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply CaretValueRules on the aggregation of a type and replace the parent values")
    void aggregationReplacesParentValues() {
      // Profile: ParentObservation
      // Parent: Observation
      // * component ^type.aggregation[0] = #contained
      // * component ^type.aggregation[1] = #referenced
      Profile parentObservation = new Profile("ParentObservation");
      parentObservation.parent = "Observation";
      CaretValueRule parentAggregationContained = caret("component", "type.aggregation[0]",
          new FshCode("contained"));
      CaretValueRule parentAggregationReferenced = caret("component", "type.aggregation[1]",
          new FshCode("referenced"));
      parentObservation.rules.addAll(rules(parentAggregationContained, parentAggregationReferenced));
      doc.profiles.put(parentObservation.name, parentObservation);
      // Profile: ChildObservation
      // Parent: ParentObservation
      // * component ^type.aggregation[0] = #contained
      Profile childObservation = new Profile("ChildObservation");
      childObservation.parent = "ParentObservation";
      CaretValueRule childAggregationContained = caret("component", "type.aggregation[0]",
          new FshCode("contained"));
      childObservation.rules.add(childAggregationContained);
      doc.profiles.put(childObservation.name, childObservation);

      exporter.export();
      StructureDefinition parentSd = pkg.profiles.get(0);
      ElementDefinition parentComponent = elementById(parentSd, "Observation.component");
      assertJsonEqual("""
          ["contained", "referenced"]""", at(parentComponent, "type[0].aggregation"));
      StructureDefinition childSd = pkg.profiles.get(1);
      ElementDefinition childComponent = elementById(childSd, "Observation.component");
      assertJsonEqual("""
          ["contained"]""", at(childComponent, "type[0].aggregation"));
      assertJsonEqual("""
          ["contained"]""", at(childComponent.calculateDiff(), "type[0].aggregation"));
    }

    private static final String CHILD_AGGREGATION_EXT = """
        [
          {
            "extension": [
              {
                "url": "http://example.org",
                "valueString": "child value"
              }
            ]
          },
          null
        ]""";

    private static final String PARENT_AGGREGATION_EXT = """
        [
          null,
          {
            "extension": [
              {
                "url": "http://example.org",
                "valueString": "parent value"
              }
            ]
          }
        ]""";

    /** Profile ChildObservation shared by the two aggregation-extension tests. */
    private void addChildAggregationObservation() {
      // Profile: ChildObservation
      // Parent: ParentObservation
      // * component ^type.aggregation[0].extension[0].url = "http://example.org"
      // * component ^type.aggregation[0].extension[0].valueString = "child value"
      // * component ^type.aggregation[0] = #contained
      // * component ^type.aggregation[1] = #bundled
      Profile childObservation = new Profile("ChildObservation");
      childObservation.parent = "ParentObservation";
      CaretValueRule childExtensionUrl = caret("component", "type.aggregation[0].extension[0].url",
          "http://example.org");
      CaretValueRule childExtensionValue = caret("component", "type.aggregation[0].extension[0].valueString",
          "child value");
      CaretValueRule childAggregationContained = caret("component", "type.aggregation[0]",
          new FshCode("contained"));
      CaretValueRule childAggregationBundled = caret("component", "type.aggregation[1]",
          new FshCode("bundled"));
      childObservation.rules.addAll(
          rules(childExtensionUrl, childExtensionValue, childAggregationContained, childAggregationBundled));
      doc.profiles.put(childObservation.name, childObservation);
    }

    private void assertChildAggregation() {
      StructureDefinition childSd = pkg.profiles.get(1);
      ElementDefinition childComponent = elementById(childSd, "Observation.component");
      assertJsonEqual("""
          ["contained", "bundled"]""", at(childComponent, "type[0].aggregation"));
      assertJsonEqual(CHILD_AGGREGATION_EXT, at(childComponent, "type[0]._aggregation"));
      ElementDefinition childComponentDiff = childComponent.calculateDiff();
      assertJsonEqual("""
          ["contained", "bundled"]""", at(childComponentDiff, "type[0].aggregation"));
      assertJsonEqual(CHILD_AGGREGATION_EXT, at(childComponentDiff, "type[0]._aggregation"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply CaretValueRules on elements within the aggregation of a type and replace the parent values")
    void aggregationElementsReplaceParentValues() {
      // Profile: ParentObservation
      // Parent: Observation
      // * component ^type.aggregation[0] = #contained
      // * component ^type.aggregation[1] = #referenced
      // * component ^type.aggregation[1].extension[0].url = "http://example.org"
      // * component ^type.aggregation[1].extension[0].valueString = "parent value"
      Profile parentObservation = new Profile("ParentObservation");
      parentObservation.parent = "Observation";
      CaretValueRule parentAggregationContained = caret("component", "type.aggregation[0]",
          new FshCode("contained"));
      CaretValueRule parentAggregationReferenced = caret("component", "type.aggregation[1]",
          new FshCode("referenced"));
      CaretValueRule parentExtensionUrl = caret("component", "type.aggregation[1].extension[0].url",
          "http://example.org");
      CaretValueRule parentExtensionValue = caret("component", "type.aggregation[1].extension[0].valueString",
          "parent value");
      parentObservation.rules.addAll(rules(parentAggregationContained, parentAggregationReferenced,
          parentExtensionUrl, parentExtensionValue));
      doc.profiles.put(parentObservation.name, parentObservation);
      addChildAggregationObservation();

      exporter.export();
      StructureDefinition parentSd = pkg.profiles.get(0);
      ElementDefinition parentComponent = elementById(parentSd, "Observation.component");
      assertJsonEqual("""
          ["contained", "referenced"]""", at(parentComponent, "type[0].aggregation"));
      assertJsonEqual(PARENT_AGGREGATION_EXT, at(parentComponent, "type[0]._aggregation"));
      assertChildAggregation();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should apply CaretValueRules on elements within the aggregation of a type and replace the children of parent values when there is no parent value")
    void aggregationElementsNoParentValue() {
      // Profile: ParentObservation
      // Parent: Observation
      // * component ^type.aggregation[1].extension[0].url = "http://example.org"
      // * component ^type.aggregation[1].extension[0].valueString = "parent value"
      Profile parentObservation = new Profile("ParentObservation");
      parentObservation.parent = "Observation";
      CaretValueRule parentExtensionUrl = caret("component", "type.aggregation[1].extension[0].url",
          "http://example.org");
      CaretValueRule parentExtensionValue = caret("component", "type.aggregation[1].extension[0].valueString",
          "parent value");
      parentObservation.rules.addAll(rules(parentExtensionUrl, parentExtensionValue));
      doc.profiles.put(parentObservation.name, parentObservation);
      addChildAggregationObservation();

      exporter.export();
      StructureDefinition parentSd = pkg.profiles.get(0);
      ElementDefinition parentComponent = elementById(parentSd, "Observation.component");
      assertJsonEqual("""
          [null, null]""", at(parentComponent, "type[0].aggregation"));
      assertJsonEqual(PARENT_AGGREGATION_EXT, at(parentComponent, "type[0]._aggregation"));
      assertChildAggregation();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CaretValueRule should output an error when a choice element has values assigned to more than one choice type")
    void multipleChoiceValues() {
      // Profile: MyObservation
      // Parent: Observation
      // * ^extension[0].url = "http://example.org/SomeExt"
      // * ^extension[0].valueString = "string value"
      // * ^extension[0].valueInteger = 7
      Profile profile = new Profile("MyObservation").<Profile>withFile("Observation.fsh")
          .withLocation(8, 3, 15, 25);
      profile.parent = "Observation";
      CaretValueRule extensionUrl = caret("", "extension[0].url", "http://example.org/SomeExt");
      CaretValueRule extensionString = caret("", "extension[0].valueString", "string value");
      CaretValueRule extensionInteger = caret("", "extension[0].valueInteger", BigInteger.valueOf(7));
      profile.rules.addAll(rules(extensionUrl, extensionString, extensionInteger));
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      assertEquals(1, size(at(sd, "extension")));
      assertJsonEqual("""
          {
            "url": "http://example.org/SomeExt",
            "valueString": "string value",
            "valueInteger": 7
          }""", at(sd, "extension[0]"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("MyObservation contains multiple choice value assignments for choice element "
          + "StructureDefinition\\.extension\\.value\\[x\\]\\..*File: Observation\\.fsh.*Line: 8 - 15\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#ObeysRule")
  class ObeysRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule at the specified path")
    void specifiedPath() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "error");
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("value[x]", "MyInvariant");
      profile.rules.add(rule); // * value[x] obeys MyInvariant

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValueX = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition changedValueX = sd.findElement("Observation.value[x]");

      assertEquals(1, size(at(baseValueX, "constraint")));
      assertEquals(2, size(at(changedValueX, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "error",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/Foo"
          }""", at(changedValueX, "constraint[1]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule at specified path (for Invariant with rules)")
    void specifiedPathWithRules() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "warning");
      AssignmentRule bestPracticeRule = assign(BEST_PRACTICE_PATH, true);
      invariant.rules = rules(bestPracticeRule);
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("value[x]", "MyInvariant");
      profile.rules.add(rule); // * value[x] obeys MyInvariant

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValueX = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition changedValueX = sd.findElement("Observation.value[x]");

      assertEquals(1, size(at(baseValueX, "constraint")));
      assertEquals(2, size(at(changedValueX, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "warning",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/Foo",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-bestpractice",
                "valueBoolean": true
              }
            ]
          }""", at(changedValueX, "constraint[1]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule at specified path (for Invariant with rules overriding keywords)")
    void specifiedPathOverridingKeywords() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "warning");
      invariant.expression = "some.fhirpath";
      invariant.xpath = "some:xpath";
      AssignmentRule keyRule = assign("key", "MyNewKey"); // BTW - super bad idea
      AssignmentRule severityRule = assign("severity", new FshCode("error"));
      AssignmentRule humanRule = assign("human", "My new description");
      AssignmentRule expressionRule = assign("expression", "new.fhirpath");
      AssignmentRule xpathRule = assign("xpath", "new:xpath");
      AssignmentRule sourceRule = assign("source", "http://example.org/fhir/StructureDefinition/new-foo");
      AssignmentRule requirementsRule = assign("requirements", "My new requirements");
      invariant.rules = rules(keyRule, severityRule, humanRule, expressionRule, xpathRule, sourceRule,
          requirementsRule);
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("value[x]", "MyInvariant");
      profile.rules.add(rule); // * value[x] obeys MyInvariant

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValueX = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition changedValueX = sd.findElement("Observation.value[x]");

      assertEquals(1, size(at(baseValueX, "constraint")));
      assertEquals(2, size(at(changedValueX, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyNewKey",
            "human": "My new description",
            "severity": "error",
            "expression": "new.fhirpath",
            "xpath": "new:xpath",
            "source": "http://example.org/fhir/StructureDefinition/new-foo",
            "requirements": "My new requirements"
          }""", at(changedValueX, "constraint[1]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule at specified path (for Invariant with soft-indexed rules)")
    void specifiedPathSoftIndexedRules() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "warning");
      AssignmentRule extension1UrlRule = assign("extension[+].url", "http://example.org/first-extension");
      AssignmentRule extension1ValueRule = assign("extension[=].valueString", "Hello");
      AssignmentRule extension2UrlRule = assign("extension[+].url", "http://example.org/second-extension");
      AssignmentRule extension2ValueRule = assign("extension[=].valueCodeableConcept",
          new FshCode("bar", "http://foo.org/codes"));
      invariant.rules = rules(extension1UrlRule, extension1ValueRule, extension2UrlRule, extension2ValueRule);
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("value[x]", "MyInvariant");
      profile.rules.add(rule); // * value[x] obeys MyInvariant

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValueX = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition changedValueX = sd.findElement("Observation.value[x]");

      assertEquals(1, size(at(baseValueX, "constraint")));
      assertEquals(2, size(at(changedValueX, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "warning",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/Foo",
            "extension": [
              {
                "url": "http://example.org/first-extension",
                "valueString": "Hello"
              },
              {
                "url": "http://example.org/second-extension",
                "valueCodeableConcept": {
                  "coding": [{ "system": "http://foo.org/codes", "code": "bar" }]
                }
              }
            ]
          }""", at(changedValueX, "constraint[1]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule at specified path (for Invariant with insert rules)")
    void specifiedPathInsertRules() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      RuleSet ruleSet = new RuleSet("BestPractice");
      AssignmentRule requirementsRule = assign("requirements", "Not required, but a really good idea.");
      AssignmentRule bestPracticeRule = assign(BEST_PRACTICE_PATH, true);
      ruleSet.rules = rules(requirementsRule, bestPracticeRule);
      doc.ruleSets.put(ruleSet.name, ruleSet);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "warning");
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "BestPractice";
      invariant.rules = rules(insertRule);
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("value[x]", "MyInvariant");
      profile.rules.add(rule); // * value[x] obeys MyInvariant

      exporter.applyInsertRules();
      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValueX = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition changedValueX = sd.findElement("Observation.value[x]");

      assertEquals(1, size(at(baseValueX, "constraint")));
      assertEquals(2, size(at(changedValueX, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "warning",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/Foo",
            "requirements": "Not required, but a really good idea.",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-bestpractice",
                "valueBoolean": true
              }
            ]
          }""", at(changedValueX, "constraint[1]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule at the path which does not have a constraint")
    void pathWithoutConstraint() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "error");
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("id", "MyInvariant");
      profile.rules.add(rule); // * id obeys MyInvariant

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition basedId = baseStructDef.findElement("Observation.id");
      ElementDefinition changedId = sd.findElement("Observation.id");

      assertNull(at(basedId, "constraint"));
      assertEquals(1, size(at(changedId, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "error",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/Foo"
          }""", at(changedId, "constraint[0]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule to the base element when no path specified")
    void baseElementNoPath() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "error");
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("", "MyInvariant");
      profile.rules.add(rule); // * obeys MyInvariant

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseElement = baseStructDef.findElement("Observation");
      ElementDefinition changedElement = sd.findElement("Observation");

      assertEquals(7, size(at(baseElement, "constraint")));
      assertEquals(8, size(at(changedElement, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "error",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/Foo"
          }""", at(changedElement, "constraint[7]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should apply an ObeysRule to the base element when no path specified (for Invariant with rules)")
    void baseElementNoPathWithRules() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "warning");
      AssignmentRule bestPracticeRule = assign(BEST_PRACTICE_PATH, true);
      invariant.rules = rules(bestPracticeRule);
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = obeys("", "MyInvariant");
      profile.rules.add(rule); // * obeys MyInvariant

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseElement = baseStructDef.findElement("Observation");
      ElementDefinition changedElement = sd.findElement("Observation");

      assertEquals(7, size(at(baseElement, "constraint")));
      assertEquals(8, size(at(changedElement, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "warning",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/Foo",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-bestpractice",
                "valueBoolean": true
              }
            ]
          }""", at(changedElement, "constraint[7]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should not apply an ObeysRule on an invariant that does not exist")
    void invariantDoesNotExist() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyRealInvariant", "A very real invariant", "error");
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = new ObeysRule("value[x]").<ObeysRule>withFile("FooProfile.fsh").withLocation(4, 7, 4, 15);
      rule.invariant = "MyFakeInvariant";
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValueX = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition changedValueX = sd.findElement("Observation.value[x]");

      assertEquals(1, size(at(baseValueX, "constraint")));
      assertEquals(1, size(at(changedValueX, "constraint")));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertMatches("Cannot apply MyFakeInvariant constraint on Foo because it was never defined.", "s",
          loggerSpy.getLastMessage());
      assertMatches("File: FooProfile\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage());
      assertEquals(1, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should log an error when applying an ObeysRule on an invariant with an invalid id")
    void invalidInvariantId() {
      // Profile: StrangeObservation
      // Parent: Observation
      // * category obeys strange_1
      // Invariant: strange_1
      // Severity: #error
      // Description: "This is a strange one."

      Profile profile = new Profile("StrangeObservation");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = new Invariant("strange_1");
      invariant.severity = new FshCode("error");
      invariant.description = "This is a strange one.";
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = new ObeysRule("category").<ObeysRule>withFile("StrangeOne.fsh").withLocation(3, 8, 3, 24);
      rule.invariant = "strange_1";
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition category = sd.findElement("Observation.category");
      assertEquals(2, size(at(category, "constraint")));
      assertEquals(invariant.name, at(category, "constraint[1].key"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: StrangeOne\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ObeysRule should log an error with correct tracking info when applying an ObeysRule with an invalid rule")
    void invalidRuleTrackingInfo() {
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      Invariant invariant = invariant("MyInvariant", "My important invariant", "warning");
      AssignmentRule invalidRule = new AssignmentRule("hockey").<AssignmentRule>withFile("MyInvariants.fsh")
          .withLocation(5, 3, 5, 14);
      invalidRule.value = "stick";
      AssignmentRule requirementsRule = assign("requirements", "My requirements.");
      AssignmentRule bestPracticeRule = assign(BEST_PRACTICE_PATH, true);

      invariant.rules = rules(requirementsRule, invalidRule, bestPracticeRule);
      doc.invariants.put(invariant.name, invariant);

      ObeysRule rule = new ObeysRule("category").<ObeysRule>withFile("MyObservation.fsh")
          .withLocation(3, 8, 3, 24);
      rule.invariant = "MyInvariant";
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition category = sd.findElement("Observation.category");
      assertEquals(2, size(at(category, "constraint")));
      assertJsonEqual("""
          {
            "key": "MyInvariant",
            "human": "My important invariant",
            "severity": "warning",
            "source": "http://hl7.org/fhir/us/minimal/StructureDefinition/MyObservation",
            "requirements": "My requirements.",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-bestpractice",
                "valueBoolean": true
              }
            ]
          }""", at(category, "constraint[1]"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertMatches("does not exist: constraint\\[1]\\.hockey", "s", loggerSpy.getLastMessage("error"));
      assertMatches("  File: MyInvariants\\.fsh.*  Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("  Applied in File: MyObservation\\.fsh.*  Applied on Line: 3\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#Extension preprocessing")
  class ExtensionPreprocessingTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should zero out Extension.value[x] when Extension.extension is used")
    void zeroValueWhenExtensionUsed() {
      Extension extension = new Extension("MyInferredComplexExtension");
      extension.setId("complex-extension");

      CardRule cardRuleForExtension = card("extension", 1, "*");
      extension.rules.add(cardRuleForExtension);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");

      assertCard(valueElement, 0, "0");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should not zero out Extension.value[x] if Extension.extension is zeroed out")
    void noZeroValueWhenExtensionZeroed() {
      Extension extension = new Extension("MyExplicitSimpleExtension");
      extension.setId("simple-extension");

      CardRule cardRuleForExtension = card("extension", 0, "0");
      extension.rules.add(cardRuleForExtension);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");

      assertCard(valueElement, 0, "1");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should log an error if Extension.extension and Extension.value[x] are both used but apply both rules")
    void errorWhenBothUsed() {
      Extension extension = new Extension("MyInvalidExtension");
      extension.setId("my-invalid-extension");

      OnlyRule onlyRuleForValueX = new OnlyRule("value[x]");
      onlyRuleForValueX.types = types(new OnlyRuleType("string"));
      extension.rules.add(onlyRuleForValueX);
      ContainsRule containsRuleForExtension = new ContainsRule("extension")
          .<ContainsRule>withFile("InvalidExtension.fsh").withLocation(4, 7, 4, 15);
      containsRuleForExtension.items = items(new ContainsRuleItem("MySlice"));
      extension.rules.add(containsRuleForExtension);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");
      ElementDefinition extensionElement = sd.findElement("Extension.extension:MySlice");

      // Both rules are applied but an error is logged
      assertJsEqual(List.of(new ElementDefinitionType("string")), valueElement.getType());
      assertNotNull(extensionElement);
      assertEquals("MySlice", at(extensionElement, "sliceName"));
      assertMatches("Extension on MyInvalidExtension cannot have both a value and sub-extensions", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: InvalidExtension\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(1, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should zero out Extension.extension when Extension.value[x] is used")
    void zeroExtensionWhenValueUsed() {
      Extension extension = new Extension("MyInferredSimpleExtension");
      extension.setId("simple-extension");

      OnlyRule onlyRuleForValue = new OnlyRule("value[x]");
      onlyRuleForValue.types = types(new OnlyRuleType("string"));
      extension.rules.add(onlyRuleForValue);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition extensionElement = sd.findElement("Extension.extension");

      assertCard(extensionElement, 0, "0");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should not zero out Extension.extension if Extension.value[x] is zeroed out")
    void noZeroExtensionWhenValueZeroed() {
      Extension extension = new Extension("MyExplicitComplexExtension");
      extension.setId("complex-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("MySlice"));
      extension.rules.add(containsRuleForExtension);

      CardRule cardRuleForValue = card("value[x]", 0, "0");
      extension.rules.add(cardRuleForValue);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition extensionElement = sd.findElement("Extension.extension");

      assertCard(extensionElement, 0, "*");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should log an error if Extension.value[x] is changed after Extension.extension is used but apply both rules")
    void errorWhenValueAfterExtension() {
      Extension extension = new Extension("MyOtherInvalidExtension");
      extension.setId("my-invalid-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("MySlice"));
      extension.rules.add(containsRuleForExtension);
      OnlyRule onlyRuleForValueX = new OnlyRule("value[x]").<OnlyRule>withFile("OtherInvalidExtension.fsh")
          .withLocation(4, 7, 4, 15);
      onlyRuleForValueX.types = types(new OnlyRuleType("string"));
      extension.rules.add(onlyRuleForValueX);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition extensionElement = sd.findElement("Extension.extension:MySlice");
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");

      // Both rules are applied but an error is logged
      assertNotNull(extensionElement);
      assertEquals("MySlice", at(extensionElement, "sliceName"));
      assertJsEqual(List.of(new ElementDefinitionType("string")), valueElement.getType());
      assertMatches("Extension on MyOtherInvalidExtension cannot have both a value and sub-extensions", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: OtherInvalidExtension\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(1, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should zero out value[x] on an extension defined inline that uses extension")
    void inlineZeroValueWhenExtensionUsed() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("mySlice"));
      extension.rules.add(containsRuleForExtension); // * extension contains MySlice

      CardRule cardRule = card("extension[mySlice].extension", 1, "*");
      // * extension[mySlice].extension 1..* which implies extension[mySlice].value[x] 0..0
      extension.rules.add(cardRule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");
      ElementDefinition mySliceValueElement = sd.findElement("Extension.extension:mySlice.value[x]");

      assertCard(valueElement, 0, "0");
      assertCard(mySliceValueElement, 0, "0");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should zero out extension on an extension defined inline that uses value[x]")
    void inlineZeroExtensionWhenValueUsed() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("mySlice"));
      extension.rules.add(containsRuleForExtension); // * extension contains MySlice

      AssignmentRule assignedValueRule = assign("extension[mySlice].valueBoolean", true);
      extension.rules.add(assignedValueRule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");
      ElementDefinition mySliceExtensionElement = sd.findElement("Extension.extension:mySlice.extension");

      assertCard(valueElement, 0, "0");
      assertCard(mySliceExtensionElement, 0, "0");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should not zero out extension if value[x] is zeroed out on an extension defined inline")
    void inlineNoZeroExtensionWhenValueZeroed() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("mySlice"));
      extension.rules.add(containsRuleForExtension); // * extension contains MySlice

      CardRule cardRule = card("extension[mySlice].value[x]", 0, "0");
      // * extension[mySlice].value[x] 0..0 which should not change anything on extension[mySlice].extension
      extension.rules.add(cardRule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");
      ElementDefinition mySliceValueElement = sd.findElement("Extension.extension:mySlice.value[x]");
      ElementDefinition mySliceExtensionElement = sd.findElement("Extension.extension:mySlice.extension");

      assertCard(valueElement, 0, "0");
      assertCard(mySliceValueElement, 0, "0");
      assertCard(mySliceExtensionElement, 0, "*"); // extension[mySlice].extension cardinality is unchanged
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should not zero out value[x] if extension is zeroed out on an extension defined inline")
    void inlineNoZeroValueWhenExtensionZeroed() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("mySlice"));
      extension.rules.add(containsRuleForExtension); // * extension contains MySlice

      CardRule cardRule = card("extension[mySlice].extension", 0, "0");
      // * extension[mySlice].extension 0..0 which should not change anything on extension[mySlice].value[x]
      extension.rules.add(cardRule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");
      ElementDefinition mySliceValueElement = sd.findElement("Extension.extension:mySlice.value[x]");
      ElementDefinition mySliceExtensionElement = sd.findElement("Extension.extension:mySlice.extension");

      assertCard(valueElement, 0, "0");
      assertCard(mySliceValueElement, 0, "1"); // extension[mySlice].value[x] cardinality is unchanged
      assertCard(mySliceExtensionElement, 0, "0");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should log an error if extension is used after value[x] on an extension defined inline and apply both rules")
    void inlineErrorExtensionAfterValue() {
      Extension extension = new Extension("MyInvalidExtension");
      extension.setId("my-invalid-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("mySlice"));
      extension.rules.add(containsRuleForExtension); // * extension contains MySlice

      // Contradictory rules - log an error but set both
      CardRule valueCardRule = card("extension[mySlice].value[x]", 1, "1");
      extension.rules.add(valueCardRule); // * extension[mySlice].value[x] 1..1
      CardRule extensionCardRule = new CardRule("extension[mySlice].extension")
          .<CardRule>withFile("InvalidInlineExtension.fsh").withLocation(4, 7, 4, 15);
      extensionCardRule.min = 1.0;
      extensionCardRule.max = "*";
      extension.rules.add(extensionCardRule); // * extension[mySlice].extension 1..*

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");
      ElementDefinition mySliceExtensionElement = sd.findElement("Extension.extension:mySlice.extension");
      ElementDefinition mySliceValueElement = sd.findElement("Extension.extension:mySlice.value[x]");

      assertCard(valueElement, 0, "0");
      assertCard(mySliceExtensionElement, 1, "*");
      assertCard(mySliceValueElement, 1, "1");
      assertMatches("Extension on MyInvalidExtension cannot have both a value and sub-extensions", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: InvalidInlineExtension\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(1, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should log an error if value[x] is used after extension on an extension defined inline and apply both rules")
    void inlineErrorValueAfterExtension() {
      Extension extension = new Extension("MyInvalidExtension");
      extension.setId("my-invalid-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("mySlice"));
      extension.rules.add(containsRuleForExtension); // * extension contains MySlice

      // Contradictory rules - log an error but set both
      CardRule extensionCardRule = card("extension[mySlice].extension", 1, "*");
      extension.rules.add(extensionCardRule); // * extension[mySlice].extension 1..*
      CardRule valueCardRule = new CardRule("extension[mySlice].value[x]")
          .<CardRule>withFile("InvalidInlineExtension.fsh").withLocation(5, 7, 5, 15);
      valueCardRule.min = 1.0;
      valueCardRule.max = "1";
      extension.rules.add(valueCardRule); // * extension[mySlice].value[x] 1..1

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition valueElement = sd.findElement("Extension.value[x]");
      ElementDefinition mySliceExtensionElement = sd.findElement("Extension.extension:mySlice.extension");
      ElementDefinition mySliceValueElement = sd.findElement("Extension.extension:mySlice.value[x]");

      assertCard(valueElement, 0, "0");
      assertCard(mySliceExtensionElement, 1, "*");
      assertCard(mySliceValueElement, 1, "1");
      assertMatches("Extension on MyInvalidExtension cannot have both a value and sub-extensions", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: InvalidInlineExtension\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(1, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should zero out value[x] if extension is used on an extension defined inline on a profile")
    void inlineOnProfileZeroValue() {
      // Other combinations of inferred CardRules on profiles are covered by the extensions tests
      Profile patientProfile = new Profile("MyPatient");
      patientProfile.parent = "Patient";

      ContainsRule containsRule = new ContainsRule("maritalStatus.extension");
      containsRule.items = items(new ContainsRuleItem("maritalSlice", "MaritalExtension"));
      CardRule sliceCardRule = card("maritalStatus.extension[maritalSlice].extension", 1, "2");
      patientProfile.rules.addAll(rules(containsRule, sliceCardRule));

      Extension extension = new Extension("MaritalExtension");
      doc.extensions.put(extension.name, extension);

      exporter.exportStructDef(patientProfile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition valueElement = sd.findElement("Patient.maritalStatus.extension:maritalSlice.value[x]");

      assertCard(valueElement, 0, "0");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should correctly allow both extension and value[x] on profiles")
    void profileAllowsBoth() {
      Profile profile = new Profile("ExtendedObservation");
      profile.parent = "Observation";

      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = items(new ContainsRuleItem("EvidenceType", "EvidenceExtension"));
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = types(new OnlyRuleType("string"));
      profile.rules.addAll(rules(containsRule, onlyRule));

      Extension extension = new Extension("EvidenceExtension");
      doc.extensions.put(extension.name, extension);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition valueElement = sd.findElement("Observation.value[x]");
      ElementDefinition extensionElement = sd.findElement("Observation.extension");

      assertCard(valueElement, 0, "1");
      assertCard(extensionElement, 0, "*");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should not add value[x] onto non-extension elements")
    void noValueOnNonExtensionElements() {
      Profile profile = new Profile("ExtendedPatient");
      profile.parent = "Patient";

      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = items(new ContainsRuleItem("PatientNote", "NoteExtension"));
      CardRule cardRule = card("extension[PatientNote].extension", 1, "1");
      profile.rules.addAll(rules(containsRule, cardRule));

      Extension noteExtension = new Extension("NoteExtension");
      doc.extensions.put(noteExtension.name, noteExtension);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extensionElement = sd.findElement("Patient.extension");
      ElementDefinition sliceExtensionElement = sd.findElement("Patient.extension:PatientNote.extension");
      ElementDefinition sliceValueElement = sd.findElement("Patient.extension:PatientNote.value[x]");

      assertCard(extensionElement, 0, "*");
      assertCard(sliceExtensionElement, 1, "1");
      assertCard(sliceValueElement, 0, "0");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should set value[x] on nested elements of a profile without zeroing extension")
    void nestedValueWithoutZeroingExtension() {
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";

      OnlyRule onlyRule = new OnlyRule("component.value[x]");
      onlyRule.types = types(new OnlyRuleType("string"));
      CardRule cardRule = card("component.extension", 1, "1");
      profile.rules.addAll(rules(onlyRule, cardRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition componentValueElement = sd.findElement("Observation.component.value[x]");
      ElementDefinition componentExtensionElement = sd.findElement("Observation.component.extension");

      assertJsEqual(new ElementDefinitionType("string"), componentValueElement.getType().get(0));
      assertCard(componentExtensionElement, 1, "1");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension preprocessing should not set inferred 0..0 CardRules if they were set on the FSH definition")
    void noInferredCardRulesWhenSet() {
      Extension extension = new Extension("MyNoInferenceExtension");
      extension.setId("my-extension");

      ContainsRule containsRuleForExtension = new ContainsRule("extension");
      containsRuleForExtension.items = items(new ContainsRuleItem("sliceA"), new ContainsRuleItem("sliceB"));
      extension.rules.add(containsRuleForExtension); // * extension contains sliceA, sliceB

      // Manually zero out value[x]/extension where appropriate
      CardRule sliceAExtensionCardRule = card("extension[sliceA].extension", 1, "*"); // * extension[sliceA].extension 1..*
      CardRule sliceAValueCardRule = card("extension[sliceA].value[x]", 0, "0"); // * extension[sliceA].value[x] 0..0

      CardRule sliceBExtensionCardRule = card("extension[sliceB].value[x]", 1, "1"); // * extension[sliceB].extension 1..1
      CardRule sliceBValueCardRule = card("extension[sliceB].extension", 0, "0"); // * extension[sliceB].extension 0..0

      extension.rules.addAll(
          rules(sliceAExtensionCardRule, sliceAValueCardRule, sliceBExtensionCardRule, sliceBValueCardRule));

      exporter.exportStructDef(extension);
      assertEquals(6, extension.rules.size());
      assertContainsRule(extension.rules.get(0), "extension", "sliceA", "sliceB");
      assertCardRule(extension.rules.get(1), "extension[sliceA].extension", 1, "*");
      assertCardRule(extension.rules.get(2), "extension[sliceA].value[x]", 0, "0");
      assertCardRule(extension.rules.get(3), "extension[sliceB].value[x]", 1, "1");
      assertCardRule(extension.rules.get(4), "extension[sliceB].extension", 0, "0");
      assertCardRule(extension.rules.get(5), "value[x]", 0, "0"); // The only rule inferred
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }
  }
}
