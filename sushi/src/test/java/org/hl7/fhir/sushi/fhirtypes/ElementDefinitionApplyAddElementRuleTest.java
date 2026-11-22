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

// port of sushi/test/fhirtypes/ElementDefinition.applyAddElementRule.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionApplyAddElementRuleTest {
  static FHIRDefinitions defs;
  StructureDefinition alternateIdentification;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    alternateIdentification = fisher.fishForStructureDefinition("AlternateIdentification");
    loggerSpy.reset();
  }

  @Nested
  @DisplayName("#applyAddElementRule()")
  class ApplyAddElementRule {
    // NOTE: ElementDefinition.applyAddElementRule(...) uses the following ElementDefinition
    //       methods:
    //         - constrainType(...)
    //         - constrainCardinality(...)
    //         - applyFlags(...)
    //       Those methods have their own exhaustive tests in other test suites; therefore,
    //       only minimal tests are provided for those aspects within the AddElementRule.

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should throw an error for an invalid AddElementRule path")
    void t2() {
      var addElementRule = new AddElementRule("foo.bar");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("boolean", null, null, null)));
      addElementRule.short_ = "A bar";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      assertThrowsContaining("The element or path you referenced does not exist: foo.bar", () -> { newElement.applyAddElementRule(addElementRule, fisher);
 });
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with minimum required attributes")
    void t3() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "provide some comments";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.comments", at(newElement, "path"));
      assertEquals(0.0, at(newElement, "min"));
      assertEquals("1", at(newElement, "max"));
      var expectedType = new ElementDefinitionType("string");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
      assertNull(at(newElement, "isModifier"));
      assertNull(at(newElement, "mustSupport"));
      assertNull(at(newElement, "isSummary"));
      assertNull(at(newElement, "extension"));
      // standards flags extensions
      assertEquals("provide some comments", at(newElement, "short"));
      assertEquals("provide some comments", at(newElement, "definition"));
      assertNotNull(at(newElement, "base"));
      assertEquals(at(newElement, "path"), at(newElement, "base.path"));
      assertEquals(at(newElement, "min"), at(newElement, "base.min"));
      assertEquals(at(newElement, "max"), at(newElement, "base.max"));
      assertNotNull(at(newElement, "constraint"));
      assertEquals(1, size(at(newElement, "constraint")));
      assertEquals("ele-1", at(newElement, "constraint[0].key"));
      assertNull(at(newElement, "constraint[0].requirements"));
      assertEquals("error", at(newElement, "constraint[0].severity"));
      assertEquals("All FHIR elements must have a @value or children", at(newElement, "constraint[0].human"));
      assertEquals("hasValue() or (children().count() > id.count())", at(newElement, "constraint[0].expression"));
      assertEquals("@value|f:*|h:div", at(newElement, "constraint[0].xpath"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Element", at(newElement, "constraint[0].source"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with multiple targetTypes")
    void t4() {
      var addElementRule = new AddElementRule("created[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("instant", null, null, null), ort("dateTime", null, null, null), ort("Period", null, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.created[x]", at(newElement, "path"));
      var expectedType1 = new ElementDefinitionType("instant");
      var expectedType2 = new ElementDefinitionType("dateTime");
      var expectedType3 = new ElementDefinitionType("Period");
      assertStrictJsEqual(list(expectedType1, expectedType2, expectedType3), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with multiple targetTypes including a reference")
    void t5() {
      var addElementRule = new AddElementRule("entity[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null), ort("uri", null, null, null), ort("Organization", true, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.entity[x]", at(newElement, "path"));
      var expectedType1 = new ElementDefinitionType("string");
      var expectedType2 = new ElementDefinitionType("uri");
      var expectedType3 = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization");
      assertStrictJsEqual(list(expectedType1, expectedType2, expectedType3), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with single Reference targetType")
    void t6() {
      var addElementRule = new AddElementRule("org");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("Organization", true, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.org", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with multiple Reference targetType")
    void t7() {
      var addElementRule = new AddElementRule("org[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("Organization", true, null, null), ort("Practitioner", true, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.org[x]", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization", "http://hl7.org/fhir/StructureDefinition/Practitioner");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with multiple targetTypes including a canonical")
    void t8() {
      var addElementRule = new AddElementRule("entity[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null), ort("uri", null, null, null), ort("Organization", null, true, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.entity[x]", at(newElement, "path"));
      var expectedType1 = new ElementDefinitionType("string");
      var expectedType2 = new ElementDefinitionType("uri");
      var expectedType3 = new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization");
      assertStrictJsEqual(list(expectedType1, expectedType2, expectedType3), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with single canonical targetType")
    void t9() {
      var addElementRule = new AddElementRule("org");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("Organization", null, true, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.org", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with multiple canonical targetType")
    void t10() {
      var addElementRule = new AddElementRule("org[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("Organization", null, true, null), ort("Practitioner", null, true, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.org[x]", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization", "http://hl7.org/fhir/StructureDefinition/Practitioner");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with multiple targetTypes including a CodeableReference")
    void t11() {
      var addElementRule = new AddElementRule("entity[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null), ort("uri", null, null, null), ort("Organization", null, null, true)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.entity[x]", at(newElement, "path"));
      var expectedType1 = new ElementDefinitionType("string");
      var expectedType2 = new ElementDefinitionType("uri");
      var expectedType3 = new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization");
      assertStrictJsEqual(list(expectedType1, expectedType2, expectedType3), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with single CodeableReference targetType")
    void t12() {
      var addElementRule = new AddElementRule("org");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("Organization", null, null, true)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.org", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with multiple CodeableReference targetType")
    void t13() {
      var addElementRule = new AddElementRule("org[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("Organization", null, null, true), ort("Practitioner", null, null, true)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.org[x]", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization", "http://hl7.org/fhir/StructureDefinition/Practitioner");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should log a warning when adding duplicate targetTypes")
    void t14() {
      var addElementRule = new AddElementRule("created[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("instant", null, null, null), ort("instant", null, null, null), ort("Period", null, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.created[x]", at(newElement, "path"));
      assertEquals(2, size(at(newElement, "type")));
      var expectedType1 = new ElementDefinitionType("instant");
      var expectedType2 = new ElementDefinitionType("Period");
      assertStrictJsEqual(list(expectedType1, expectedType2), at(newElement, "type"));
      assertEquals("created[x] includes duplicate types. Duplicates have been ignored.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should not log a duplicate warning when adding multiple Reference and canonical and CodeableReference targetTypes")
    void t15() {
      var addElementRule = new AddElementRule("prop1[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null), ort("uri", null, null, null), ort("Organization", true, null, null), ort("Practitioner", true, null, null), ort("Organization", null, true, null), ort("Practitioner", null, true, null), ort("Organization", null, null, true), ort("Practitioner", null, null, true)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
      assertEquals("AlternateIdentification.prop1[x]", at(newElement, "path"));
      var expectedType1 = new ElementDefinitionType("string");
      var expectedType2 = new ElementDefinitionType("uri");
      var expectedType3 = new ElementDefinitionType("Reference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization", "http://hl7.org/fhir/StructureDefinition/Practitioner");
      var expectedType4 = new ElementDefinitionType("canonical").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization", "http://hl7.org/fhir/StructureDefinition/Practitioner");
      var expectedType5 = new ElementDefinitionType("CodeableReference").withTargetProfiles("http://hl7.org/fhir/StructureDefinition/Organization", "http://hl7.org/fhir/StructureDefinition/Practitioner");
      assertStrictJsEqual(list(expectedType1, expectedType2, expectedType3, expectedType4, expectedType5), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should throw an error when invalid path for multiple targetTypes")
    void t16() {
      var addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null), ort("Annotation", null, null, null)));
      addElementRule.short_ = "prop1 definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      assertThrowsContaining("As a FHIR choice data type, the specified prop1 for AddElementRule must end with '[x]'.", () -> { newElement.applyAddElementRule(addElementRule, fisher);
 });
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with single Profile targetType using the profile id")
    void t17() {
      var addElementRule = new AddElementRule("patient");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("us-core-patient", null, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.patient", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("Patient").withProfiles("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with single Profile targetType using the profile name")
    void t18() {
      var addElementRule = new AddElementRule("patient");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("USCorePatientProfile", null, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.patient", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("Patient").withProfiles("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with single Logical Model targetType using the model id")
    void t19() {
      var addElementRule = new AddElementRule("serviceModel");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("eLTSSServiceModel", null, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.serviceModel", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("eLTSSServiceModel");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with single Logical Model targetType using the model name")
    void t20() {
      var addElementRule = new AddElementRule("serviceModel");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("ELTSSServiceModel", null, null, null)));
      addElementRule.short_ = "short definition";
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.serviceModel", at(newElement, "path"));
      var expectedType = new ElementDefinitionType("eLTSSServiceModel");
      assertStrictJsEqual(list(expectedType), at(newElement, "type"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with all boolean flags set to true")
    void t21() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "short definition";
      addElementRule.summary = true;
      addElementRule.modifier = true;
      // MustSupport must be false/undefined for logical models and resources;
      // otherwise, error will be thrown - tested elsewhere
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.comments", at(newElement, "path"));
      assertFalse(Js.truthy(at(newElement, "mustSupport")));
      assertEquals(true, at(newElement, "isSummary"));
      assertEquals(true, at(newElement, "isModifier"));
      assertNull(at(newElement, "extension"));
      // standards flags extensions
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with all boolean flags set to false")
    void t22() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "short definition";
      addElementRule.mustSupport = false;
      addElementRule.summary = false;
      addElementRule.modifier = false;
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.comments", at(newElement, "path"));
      // When flags are set to false, the attributes are not generated rather than set to false
      assertNull(at(newElement, "mustSupport"));
      assertNull(at(newElement, "isSummary"));
      assertNull(at(newElement, "isModifier"));
      assertNull(at(newElement, "extension"));
      // standards flags extensions
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with trial use standards flag set to true")
    void t23() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = true;
      addElementRule.normative = false;
      addElementRule.draft = false;
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.comments", at(newElement, "path"));
      // When standards flags are set to true, an extension is created
      assertEquals(1, size(at(newElement, "extension")));
      assertJsEqual(obj("url", "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status", "valueCode", "trial-use"), at(newElement, "extension[0]"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with normative standards flag set to true")
    void t24() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = false;
      addElementRule.normative = true;
      addElementRule.draft = false;
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.comments", at(newElement, "path"));
      // When standards flags are set to true, an extension is created
      assertEquals(1, size(at(newElement, "extension")));
      assertJsEqual(obj("url", "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status", "valueCode", "normative"), at(newElement, "extension[0]"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with draft standards flag set to true")
    void t25() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = false;
      addElementRule.normative = false;
      addElementRule.draft = true;
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.comments", at(newElement, "path"));
      // When standards flags are set to true, an extension is created
      assertEquals(1, size(at(newElement, "extension")));
      assertJsEqual(obj("url", "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status", "valueCode", "draft"), at(newElement, "extension[0]"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with all standards flags set to false")
    void t26() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = false;
      addElementRule.normative = false;
      addElementRule.draft = false;
      var newElement = alternateIdentification.newElement(addElementRule.path);
      newElement.applyAddElementRule(addElementRule, fisher);
      assertEquals("AlternateIdentification.comments", at(newElement, "path"));
      // When standards flags are set to false, the underlying extension is not created
      assertNull(at(newElement, "extension"));
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should throw an error when apply AddElementRule with multiple standards flags set to true")
    void t27() {
      var addElementRule = new AddElementRule("comments");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = true;
      addElementRule.normative = true;
      addElementRule.draft = true;
      var newElement = alternateIdentification.newElement(addElementRule.path);
      assertThrowsContaining("Cannot apply multiple standards status on AlternateIdentification.comments", () -> { newElement.applyAddElementRule(addElementRule, fisher);
 });
    }

    @Test
    @DisplayName("ElementDefinition #applyAddElementRule() should apply AddElementRule with supported doc attributes")
    void t28() {
      var addElementRule1 = new AddElementRule("prop1");
      addElementRule1.min = 0.0;
      addElementRule1.max = "1";
      addElementRule1.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule1.short_ = "short description for prop1";
      addElementRule1.definition = "definition for prop1";
      var addElementRule2 = new AddElementRule("prop2");
      addElementRule2.min = 0.0;
      addElementRule2.max = "1";
      addElementRule2.types = new ArrayList<>(List.of(ort("string", null, null, null)));
      addElementRule2.short_ = "short description for prop2";
      var newElement1 = alternateIdentification.newElement(addElementRule1.path);
      newElement1.applyAddElementRule(addElementRule1, fisher);
      assertEquals("AlternateIdentification.prop1", at(newElement1, "path"));
      assertEquals(addElementRule1.short_, at(newElement1, "short"));
      assertEquals(addElementRule1.definition, at(newElement1, "definition"));
      var newElement2 = alternateIdentification.newElement(addElementRule2.path);
      newElement2.applyAddElementRule(addElementRule2, fisher);
      assertEquals("AlternateIdentification.prop2", at(newElement2, "path"));
      assertEquals(addElementRule2.short_, at(newElement2, "short"));
      // definition gets defaulted to short
      assertEquals(addElementRule2.short_, at(newElement2, "definition"));
    }
  }
}
