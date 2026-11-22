package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertContainsText;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.ExtensionContext;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R4' > '#StructureDefinition', '#Parents' (incl. 'Issue #1553 Bug Fix'), '#Profile',
// '#Profile-Element', '#Extension', '#LogicalModel', '#Resource', '#Invariant', '#Rules' (TS lines 58-2473).
// The outer beforeAll/beforeEach come from SdeTestBase (r4Defs()).
@DisplayName("StructureDefinitionExporter R4")
class StructureDefinitionExporterR4ATest extends SdeTestBase {

  /** Jest expect(fn).toThrow(msg): throws, and the message contains msg. */
  static void assertThrowsMessage(String expectedSubstring, Executable fn) {
    Throwable t = assertThrows(Throwable.class, fn);
    assertContainsText(expectedSubstring, t.getMessage());
  }

  /** TS sds.find(p => p.id === id). */
  static StructureDefinition findById(List<StructureDefinition> sds, String id) {
    return sds.stream().filter(sd -> id.equals(at(sd, "id"))).findFirst().orElse(null);
  }

  /** TS sd.elements.find(e => e.id === id). */
  static ElementDefinition elementById(StructureDefinition sd, String id) {
    return sd.elements.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
  }

  /** Rebuilds tank/pkg/fisher/exporter over a FSHOnly config with status and version (TS cloneDeep(minimalConfig)). */
  void useFshOnlyConfig() {
    // Create a FSHOnly config with a status and version
    Configuration fshOnlyConfig = minimalConfig();
    fshOnlyConfig.FSHOnly = true;
    fshOnlyConfig.version = "0.1.0";
    fshOnlyConfig.status = "active";
    FSHTank input = new FSHTank(new ArrayList<>(List.of(doc)), fshOnlyConfig);
    pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    exporter = new StructureDefinitionExporter(input, pkg, fisher);
  }

  static final String CODEGEN_SUPER_BASE_DEFINITION = """
      {
        "extension": [
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-codegen-super",
            "valueString": "MetadataResource"
          }
        ]
      }""";

  @Nested
  @DisplayName("#StructureDefinition")
  class StructureDefinitionTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should not export duplicate structure definitions")
    void shouldNotExportDuplicateStructureDefinitions() {
      Profile profile = new Profile("Foo");
      profile.parent = "Patient";
      doc.profiles.put(profile.name, profile);

      Extension extension = new Extension("Bar");
      extension.setId("bar");
      doc.aliases.put("barAlias", "Bar");
      doc.extensions.put(extension.name, extension);

      ContainsRule ruleBar = new ContainsRule("extension");
      ruleBar.items = new ArrayList<>(List.of(new ContainsRuleItem("bar", "barAlias")));
      profile.rules.add(ruleBar);

      Package pkg = exporter.export();
      assertEquals(1, pkg.profiles.size());
      assertEquals(1, pkg.extensions.size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should warn when the structDef is a profile and title and/or description is an empty string")
    void shouldWarnProfileEmptyTitleDescription() {
      Profile profile = new Profile("Foo");
      profile.parent = "Patient";
      profile.title = "";
      profile.description = "";
      doc.profiles.put(profile.name, profile);
      Package exported = exporter.export();
      assertEquals(1, exported.profiles.size());

      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Profile Foo has a title field that should not be empty.", loggerSpy.getFirstMessage("warn"));
      assertContainsText("Profile Foo has a description field that should not be empty.",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should warn when the structDef is an extension and title and/or description is an empty string")
    void shouldWarnExtensionEmptyTitleDescription() {
      Extension extension = new Extension("Bar");
      extension.parent = "Extension";
      extension.title = "";
      extension.description = "";
      doc.extensions.put(extension.name, extension);
      Package exported = exporter.export();

      assertEquals(1, exported.extensions.size());

      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Extension Bar has a title field that should not be empty.",
          loggerSpy.getFirstMessage("warn"));
      assertContainsText("Extension Bar has a description field that should not be empty.",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should warn when the structDef is a logical and title and/or description is an empty string")
    void shouldWarnLogicalEmptyTitleDescription() {
      Logical logical = new Logical("BackFooTheFuture");
      logical.parent = "Element";
      logical.title = "";
      logical.description = "";
      doc.logicals.put(logical.name, logical);
      Package exported = exporter.export();

      assertEquals(1, exported.logicals.size());

      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Logical BackFooTheFuture has a title field that should not be empty.",
          loggerSpy.getFirstMessage("warn"));
      assertContainsText("Logical BackFooTheFuture has a description field that should not be empty.",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should warn when the structDef is a resource and title and/or description is an empty string")
    void shouldWarnResourceEmptyTitleDescription() {
      Resource resource = new Resource("SpidermanBarFromHome");
      resource.parent = "Resource";
      resource.setId("PatientResource");
      resource.title = "";
      resource.description = "";
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);

      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Resource SpidermanBarFromHome has a title field that should not be empty.",
          loggerSpy.getFirstMessage("warn"));
      assertContainsText("Resource SpidermanBarFromHome has a description field that should not be empty.",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should log a message when the structure definition has an invalid id")
    void shouldLogInvalidId() {
      Profile profile = new Profile("Wrong").withFile("Wrong.fsh").withLocation(1, 8, 4, 18);
      profile.setId("will?not?work");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("will?not?work", at(exported, "id"));
      assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage());
      assertMatches("File: Wrong\\.fsh.*Line: 1 - 4\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should not log a message when the structure definition overrides an invalid id with a Caret Rule")
    void shouldNotLogInvalidIdOverriddenByCaretRule() {
      Profile profile = new Profile("Wrong").withFile("Wrong.fsh").withLocation(1, 8, 4, 18);
      profile.setId("will?not?work");
      profile.parent = "Patient";
      CaretValueRule idRule = new CaretValueRule("");
      idRule.caretPath = "id";
      idRule.value = "will-work";
      profile.rules.add(idRule);
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("will-work", at(exported, "id"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should log a message when the structure definition overrides an invalid id with an invalid Caret Rule")
    void shouldLogInvalidIdOverriddenByInvalidCaretRule() {
      Profile profile = new Profile("Wrong").withFile("Wrong.fsh").withLocation(1, 8, 4, 18);
      profile.setId("will?not?work");
      profile.parent = "Patient";
      CaretValueRule idRule = new CaretValueRule("").withFile("Wrong.fsh").withLocation(3, 8, 3, 18);
      idRule.caretPath = "id";
      idRule.value = "Still Wont Work!";
      profile.rules.add(idRule);
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Still Wont Work!", at(exported, "id"));
      assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage());
      assertMatches("File: Wrong\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should log a message when the structure definition overrides an valid id with an invalid Caret Rule")
    void shouldLogValidIdOverriddenByInvalidCaretRule() {
      Profile profile = new Profile("Wrong").withFile("Wrong.fsh").withLocation(1, 8, 4, 18);
      profile.setId("valid-id");
      profile.parent = "Observation";
      CaretValueRule idRule = new CaretValueRule("").withFile("Wrong.fsh").withLocation(3, 8, 3, 18);
      idRule.caretPath = "id";
      idRule.value = "This Is Not Right!";
      profile.rules.add(idRule);
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("This Is Not Right!", at(exported, "id"));
      assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage());
      assertMatches("File: Wrong\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should log a message when the structure definition has an invalid name")
    void shouldLogInvalidName() {
      Profile profile = new Profile("Not-good").withFile("Wrong.fsh").withLocation(2, 8, 5, 18);
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Not-good", at(exported, "name"));
      assertMatches("may not be suitable for machine processing applications such as code generation", "s",
          loggerSpy.getLastMessage());
      assertMatches("File: Wrong\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should not log a message when the structure definition overrides an invalid name with a Caret Rule")
    void shouldNotLogInvalidNameOverriddenByCaretRule() {
      Profile profile = new Profile("Not-good").withFile("Wrong.fsh").withLocation(2, 8, 5, 18);
      profile.parent = "Observation";
      CaretValueRule nameRule = new CaretValueRule("");
      nameRule.caretPath = "name";
      nameRule.value = "NotGood";
      profile.rules.add(nameRule);
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("NotGood", at(exported, "name"));
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should log a message when the structure definition overrides an invalid name with an invalid Caret Rule")
    void shouldLogInvalidNameOverriddenByInvalidCaretRule() {
      Profile profile = new Profile("Not-good").withFile("Wrong.fsh").withLocation(2, 8, 5, 18);
      profile.parent = "Observation";
      CaretValueRule nameRule = new CaretValueRule("").withFile("Wrong.fsh").withLocation(3, 8, 3, 18);
      nameRule.caretPath = "name";
      nameRule.value = "Not-good";
      profile.rules.add(nameRule);
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Not-good", at(exported, "name"));
      assertMatches("may not be suitable for machine processing applications such as code generation", "s",
          loggerSpy.getLastMessage());
      assertMatches("File: Wrong\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should log a message when the structure definition overrides a valid name with an invalid Caret Rule")
    void shouldLogValidNameOverriddenByInvalidCaretRule() {
      Profile profile = new Profile("NotGood").withFile("Wrong.fsh").withLocation(2, 8, 5, 18);
      profile.parent = "Patient";
      CaretValueRule nameRule = new CaretValueRule("").withFile("Wrong.fsh").withLocation(3, 8, 3, 18);
      nameRule.caretPath = "name";
      nameRule.value = "Not-good";
      profile.rules.add(nameRule);
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Not-good", at(exported, "name"));
      assertMatches("may not be suitable for machine processing applications such as code generation", "s",
          loggerSpy.getLastMessage());
      assertMatches("File: Wrong\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should sanitize the id and log a message when a valid name is used to make an invalid id")
    void shouldSanitizeIdFromValidName() {
      Profile profile = new Profile("Not_good_id").withFile("Wrong.fsh").withLocation(2, 8, 5, 18);
      profile.parent = "Patient";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Not_good_id", at(exported, "name"));
      assertEquals("Not-good-id", at(exported, "id"));
      assertMatches(
          "The string \"Not_good_id\" represents a valid FHIR name but not a valid FHIR id.*The id will be exported as \"Not-good-id\"",
          "s", loggerSpy.getLastMessage("warn"));
      assertMatches("File: Wrong\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should sanitize the id and log a message when a long valid name is used to make an invalid id")
    void shouldSanitizeIdFromLongValidName() {
      String longId = "Toolong";
      while (longId.length() < 65) longId += "longer";
      Profile profile = new Profile(longId).withFile("Wrong.fsh").withLocation(2, 8, 5, 18);
      profile.parent = "Basic";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      String expectedId = longId.substring(0, 64);
      assertEquals(longId, at(exported, "name"));
      assertEquals(expectedId, at(exported, "id"));
      String warning = "The string \"" + longId + "\" represents a valid FHIR name but not a valid FHIR id.*The id will be exported as \""
          + expectedId + "\"";
      assertMatches(warning, "s", loggerSpy.getLastMessage("warn"));
      assertMatches("File: Wrong\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @Disabled("needs jest mock: jest.spyOn(StructureDefinition.prototype, 'validate').mockReturnValue([...]); the exporter"
        + " builds its own StructureDefinition (no injection point to substitute a subclass)")
    @DisplayName("StructureDefinitionExporter R4 #StructureDefinition should log error messages for validation errors on the StructureDefinition")
    void shouldLogValidationErrors() {
      Profile profile = new Profile("MyPatientProfile");
      profile.parent = "Patient";
      profile.setId("my-patient");
      doc.profiles.put(profile.name, profile);

      // TS: StructureDefinition.prototype.validate mocked to return
      // [new ValidationError('issue1', 'fhirPath1'), new ValidationError('issue2', 'fhirPath2')]

      exporter.exportStructDef(profile);

      assertMatches("fhirPath2: issue2", loggerSpy.getMessageAtIndex(-1, "error"));
      assertMatches("fhirPath1: issue1", loggerSpy.getMessageAtIndex(-2, "error"));

      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("MyPatientProfile", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Patient", at(exported, "baseDefinition"));
    }
  }

  @Nested
  @DisplayName("#Parents")
  class ParentsTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a profile when the definition specifies a resource for a parent")
    void profileWithResourceParent() {
      Profile profile = new Profile("MyPatientProfile");
      profile.parent = "Patient";
      profile.setId("my-patient");
      doc.profiles.put(profile.name, profile);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(profile);
      });

      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("MyPatientProfile", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Patient", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a profile when the definition specifies another profile for a parent")
    void profileWithProfileParent() {
      Profile profile = new Profile("MyPatientProfile");
      profile.parent = "us-core-patient";
      profile.setId("my-patient");
      doc.profiles.put(profile.name, profile);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(profile);
      });

      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("MyPatientProfile", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient", at(exported, "baseDefinition"));
    }

    @Nested
    @DisplayName("Issue #1553 Bug Fix")
    class Issue1553BugFixTests {
      // Issue #1553 - https://github.com/FHIR/sushi/issues/1553
      // Note: `testdefs` for R4 does not include a StructureDefinition for us-core-allergyintolerance
      // so substitute it with Patient and the available version of us-core-patient:
      // http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient having version 3.1.0

      @Test
      @DisplayName("StructureDefinitionExporter R4 #Parents Issue #1553 Bug Fix should create a profile when the definition specifies another profile does not have a canonical version for the parent")
      void parentWithoutCanonicalVersion() {
        Profile profile = new Profile("Patient");
        profile.parent = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient";
        profile.setId("my-patient");
        doc.profiles.put(profile.name, profile);

        assertDoesNotThrow(() -> {
          exporter.exportStructDef(profile);
        });

        StructureDefinition exported = pkg.profiles.get(0);
        assertEquals("Patient", at(exported, "name"));
        assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient",
            at(exported, "baseDefinition"));
      }

      @Test
      @DisplayName("StructureDefinitionExporter R4 #Parents Issue #1553 Bug Fix should create profiles when the definition specifies a different canonical version for the parent")
      void parentWithDifferentCanonicalVersions() {
        Profile profile310 = new Profile("Patient310");
        profile310.parent = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0";
        profile310.setId("my-patient-310");
        doc.profiles.put(profile310.name, profile310);

        Profile profile400 = new Profile("Patient400");
        profile400.parent = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|4.0.0";
        profile400.setId("my-patient-400");
        doc.profiles.put(profile400.name, profile400);

        assertDoesNotThrow(() -> {
          exporter.exportStructDef(profile310);
          exporter.exportStructDef(profile400);
        });

        StructureDefinition exported310 = findById(pkg.profiles, "my-patient-310");
        assertEquals("Patient310", at(exported310, "name"));
        assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0",
            at(exported310, "baseDefinition"));

        StructureDefinition exported400 = findById(pkg.profiles, "my-patient-400");
        assertEquals("Patient400", at(exported400, "name"));
        assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|4.0.0",
            at(exported400, "baseDefinition"));
      }

      @Test
      @DisplayName("StructureDefinitionExporter R4 #Parents Issue #1553 Bug Fix should throw an Error when the definition specifies another profile having an unsupported canonical version for the parent")
      void parentWithUnsupportedCanonicalVersion() {
        Profile profile = new Profile("Patient");
        profile.parent = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|1.2.3";
        profile.setId("my-patient");
        doc.profiles.put(profile.name, profile);

        Executable t = () -> {
          exporter.exportStructDef(profile);
        };
        assertThrows(RuntimeException.class, t);
        assertThrowsMessage(
            "Parent http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|1.2.3 not found for Patient", t);
      }

      @Test
      @DisplayName("StructureDefinitionExporter R4 #Parents Issue #1553 Bug Fix should throw an Error when the definition specifies another profile having an unexpected canonical version for the parent")
      void parentWithUnexpectedCanonicalVersion() {
        Profile profile = new Profile("Patient");
        profile.parent = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|wierd|version";
        profile.setId("my-patient");
        doc.profiles.put(profile.name, profile);

        Executable t = () -> {
          exporter.exportStructDef(profile);
        };
        assertThrows(RuntimeException.class, t);
        assertThrowsMessage(
            "Parent http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|wierd|version not found for Patient",
            t);
      }
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a profile when the definition specifies a complex data type for a parent")
    void profileWithComplexTypeParent() {
      Profile profile = new Profile("MyAddressProfile");
      profile.parent = "Address";
      profile.setId("my-address");
      doc.profiles.put(profile.name, profile);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(profile);
      });

      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("MyAddressProfile", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Address", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a profile when the definition specifies a primitive data type for a parent")
    void profileWithPrimitiveTypeParent() {
      Profile profile = new Profile("MyDateTimeProfile");
      profile.parent = "dateTime";
      profile.setId("my-datetime");
      doc.profiles.put(profile.name, profile);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(profile);
      });

      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("MyDateTimeProfile", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/dateTime", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create an extension with default parent of base Extension when the definition does not specify a parent")
    void extensionWithDefaultParent() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");
      doc.extensions.put(extension.name, extension);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(extension);
      });

      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("MyExtension", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Extension", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create an extension when the definition specifies the base Extension for a parent")
    void extensionWithBaseExtensionParent() {
      Extension extension = new Extension("MyExtension");
      extension.parent = "Extension";
      extension.setId("my-extension");
      doc.extensions.put(extension.name, extension);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(extension);
      });

      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("MyExtension", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Extension", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create an extension when the definition specifies another extension for a parent")
    void extensionWithExtensionParent() {
      Extension extension = new Extension("MyFamilyHistoryExtension");
      extension.parent = "familymemberhistory-type";
      extension.setId("my-family-history");
      doc.extensions.put(extension.name, extension);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(extension);
      });

      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("MyFamilyHistoryExtension", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/familymemberhistory-type",
          at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a logical model with default parent of Base when the definition does not specify a parent")
    void logicalWithDefaultParent() {
      Logical logical = new Logical("MyPatientModel");
      logical.setId("PatientModel");
      doc.logicals.put(logical.name, logical);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(logical);
      });

      StructureDefinition exported = pkg.logicals.get(0);
      assertEquals("MyPatientModel", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a logical model when the definition specifies Element for a parent")
    void logicalWithElementParent() {
      Logical logical = new Logical("MyPatientModel");
      logical.parent = "Element";
      logical.setId("PatientModel");
      doc.logicals.put(logical.name, logical);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(logical);
      });

      StructureDefinition exported = pkg.logicals.get(0);
      assertEquals("MyPatientModel", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Element", at(exported, "baseDefinition"));
      // When the parent is defined as Element, the Element.extension values
      // should not have been brought forward
      assertNull(at(exported.elements.get(0), "extension"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a logical model when the definition specifies another logical model for a parent")
    void logicalWithLogicalParent() {
      Logical logical = new Logical("MyIdentificationModel");
      logical.parent = "AlternateIdentification";
      logical.setId("my-identification");
      doc.logicals.put(logical.name, logical);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(logical);
      });

      StructureDefinition exported = pkg.logicals.get(0);
      assertEquals("MyIdentificationModel", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/cda/StructureDefinition/AlternateIdentification",
          at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a resource with default parent of DomainResource when the definition does not specify a parent")
    void resourceWithDefaultParent() {
      Resource resource = new Resource("MyPatientResource");
      resource.setId("PatientResource");
      doc.resources.put(resource.name, resource);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(resource);
      });

      StructureDefinition exported = pkg.resources.get(0);
      assertEquals("MyPatientResource", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should create a resource when the definition specifies Resource for a parent")
    void resourceWithResourceParent() {
      Resource resource = new Resource("MyPatientResource");
      resource.parent = "Resource";
      resource.setId("PatientResource");
      doc.resources.put(resource.name, resource);

      assertDoesNotThrow(() -> {
        exporter.exportStructDef(resource);
      });

      StructureDefinition exported = pkg.resources.get(0);
      assertEquals("MyPatientResource", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Resource", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentNotProvidedError when parent specifies an empty parent")
    void throwParentNotProvided() {
      Profile profile = new Profile("Foo");
      profile.parent = "";
      doc.profiles.put(profile.name, profile);
      assertThrowsMessage("The definition for Foo does not include a Parent", () -> {
        exporter.exportStructDef(profile);
      });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentNotDefinedError when parent is not found")
    void throwParentNotDefined() {
      Profile profile = new Profile("Foo");
      profile.parent = "Bar";
      doc.profiles.put(profile.name, profile);
      assertThrowsMessage("Parent Bar not found for Foo", () -> {
        exporter.exportStructDef(profile);
      });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsNameError when the extension declares itself as the parent")
    void throwParentDeclaredAsNameExtension() {
      Extension extension = new Extension("Foo");
      extension.parent = "Foo";
      doc.extensions.put(extension.name, extension);
      assertThrowsMessage("Extension \"Foo\" cannot declare itself as a Parent.", () -> {
        exporter.exportStructDef(extension);
      });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsIdError when a extension sets the same value for parent and id")
    void throwParentDeclaredAsIdExtension() {
      Extension parentExtension = new Extension("InitialExtension");
      parentExtension.setId("ParentExtension");
      doc.extensions.put(parentExtension.name, parentExtension);

      Extension childExtension = new Extension("OverlappingExtension");
      childExtension.parent = "InitialExtension";
      childExtension.setId("InitialExtension");
      doc.extensions.put(childExtension.name, childExtension);

      assertThrowsMessage(
          "Extension \"OverlappingExtension\" cannot declare \"InitialExtension\" as both Parent and Id.", () -> {
            exporter.exportStructDef(childExtension);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsNameError when the profile declares itself as the parent")
    void throwParentDeclaredAsNameProfile() {
      Profile profile = new Profile("Foo");
      profile.parent = "Foo";
      doc.profiles.put(profile.name, profile);
      assertThrowsMessage("Profile \"Foo\" cannot declare itself as a Parent.", () -> {
        exporter.exportStructDef(profile);
      });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsNameError and suggest resource URL when the profile declares itself as the parent and it is a FHIR resource")
    void throwParentDeclaredAsNameProfileFhirResource() {
      Profile profile = new Profile("Patient");
      profile.parent = "Patient";
      doc.profiles.put(profile.name, profile);
      assertThrowsMessage(
          "Profile \"Patient\" cannot declare itself as a Parent. It looks like the parent is an external resource; use its URL (e.g., http://hl7.org/fhir/StructureDefinition/Patient).",
          () -> {
            exporter.exportStructDef(profile);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsIdError when a profile sets the same value for parent and id")
    void throwParentDeclaredAsIdProfile() {
      Profile parentProfile = new Profile("InitialProfile");
      parentProfile.setId("ParentProfile");
      doc.profiles.put(parentProfile.name, parentProfile);

      Profile childProfile = new Profile("OverlappingProfile");
      childProfile.parent = "InitialProfile";
      childProfile.setId("InitialProfile");
      doc.profiles.put(childProfile.name, childProfile);

      assertThrowsMessage("Profile \"OverlappingProfile\" cannot declare \"InitialProfile\" as both Parent and Id.",
          () -> {
            exporter.exportStructDef(childProfile);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsIdError and suggest resource URL when a profile sets the same value for parent and id and the parent is a FHIR resource")
    void throwParentDeclaredAsIdProfileFhirResource() {
      Profile profile = new Profile("KidsFirstPatient");
      profile.parent = "Patient";
      profile.setId("Patient");
      doc.profiles.put(profile.name, profile);
      assertThrowsMessage(
          "Profile \"KidsFirstPatient\" cannot declare \"Patient\" as both Parent and Id. It looks like the parent is an external resource; use its URL (e.g., http://hl7.org/fhir/StructureDefinition/Patient).",
          () -> {
            exporter.exportStructDef(profile);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsNameError when the resource declares itself as the parent")
    void throwParentDeclaredAsNameResource() {
      Resource resource = new Resource("Foo");
      resource.parent = "Foo";
      doc.resources.put(resource.name, resource);
      assertThrowsMessage("Resource \"Foo\" cannot declare itself as a Parent.", () -> {
        exporter.exportStructDef(resource);
      });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsIdError when a resource sets the same value for parent and id")
    void throwParentDeclaredAsIdResource() {
      Resource parentResource = new Resource("InitialResource");
      parentResource.setId("ParentExtension");
      doc.resources.put(parentResource.name, parentResource);

      Resource childResource = new Resource("OverlappingResource");
      childResource.parent = "InitialResource";
      childResource.setId("InitialResource");
      doc.resources.put(childResource.name, childResource);

      assertThrowsMessage(
          "Resource \"OverlappingResource\" cannot declare \"InitialResource\" as both Parent and Id.", () -> {
            exporter.exportStructDef(childResource);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsNameError when the logical model declares itself as the parent")
    void throwParentDeclaredAsNameLogical() {
      Logical logical = new Logical("Foo");
      logical.parent = "Foo";
      doc.logicals.put(logical.name, logical);
      assertThrowsMessage("Logical \"Foo\" cannot declare itself as a Parent.", () -> {
        exporter.exportStructDef(logical);
      });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsNameError and suggest resource URL when the logical model declares itself as the parent and it is a FHIR resource")
    void throwParentDeclaredAsNameLogicalFhirResource() {
      Logical logical = new Logical("Patient");
      logical.parent = "Patient";
      doc.logicals.put(logical.name, logical);
      assertThrowsMessage(
          "Logical \"Patient\" cannot declare itself as a Parent. It looks like the parent is an external resource; use its URL (e.g., http://hl7.org/fhir/StructureDefinition/Patient).",
          () -> {
            exporter.exportStructDef(logical);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsIdError when a logical model sets the same value for parent and id")
    void throwParentDeclaredAsIdLogical() {
      Logical parentLogical = new Logical("InitialLogical");
      parentLogical.setId("ParentLogical");
      doc.logicals.put(parentLogical.name, parentLogical);

      Logical childLogical = new Logical("OverlappingLogical");
      childLogical.parent = "InitialLogical";
      childLogical.setId("InitialLogical");
      doc.logicals.put(childLogical.name, childLogical);

      assertThrowsMessage("Logical \"OverlappingLogical\" cannot declare \"InitialLogical\" as both Parent and Id.",
          () -> {
            exporter.exportStructDef(childLogical);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw ParentDeclaredAsIdError and suggest resource URL when a logical model sets the same value for parent and id and the parent is a FHIR resource")
    void throwParentDeclaredAsIdLogicalFhirResource() {
      Logical logical = new Logical("KidsFirstPatient");
      logical.parent = "Patient";
      logical.setId("Patient");
      doc.logicals.put(logical.name, logical);
      assertThrowsMessage(
          "Logical \"KidsFirstPatient\" cannot declare \"Patient\" as both Parent and Id. It looks like the parent is an external resource; use its URL (e.g., http://hl7.org/fhir/StructureDefinition/Patient).",
          () -> {
            exporter.exportStructDef(logical);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw InvalidExtensionParentError when an extension has a non-extension for a parent")
    void throwInvalidExtensionParent() {
      Extension extension = new Extension("MyPatientExtension");
      extension.parent = "Patient";
      extension.setId("PatientExt");
      doc.extensions.put(extension.name, extension);
      assertThrowsMessage(
          "Invalid parent Patient specified for extension MyPatientExtension. The parent of an extension must be the base Extension or another defined extension.",
          () -> {
            exporter.exportStructDef(extension);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw InvalidLogicalParentError when a logical model has a profile for a parent")
    void throwInvalidLogicalParent() {
      Logical logical = new Logical("MyGroupModel");
      logical.parent = "actualgroup"; // Profile
      logical.setId("GroupModel");
      doc.logicals.put(logical.name, logical);
      assertThrowsMessage(
          "Invalid parent Actual Group specified for logical model MyGroupModel. The parent of a logical model must be Element, Base, another logical model, a resource, or a type.",
          () -> {
            exporter.exportStructDef(logical);
          });
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Parents should throw InvalidResourceParentError when a resource does not have Resource or DomainResource for a parent")
    void throwInvalidResourceParent() {
      Resource resource = new Resource("MyCustomPatient");
      resource.parent = "Patient";
      resource.setId("CustomPatient");
      doc.resources.put(resource.name, resource);
      assertThrowsMessage(
          "Invalid parent Patient specified for resource MyCustomPatient. The parent of a resource must be Resource or DomainResource.",
          () -> {
            exporter.exportStructDef(resource);
          });
    }
  }

  @Nested
  @DisplayName("#Profile")
  class ProfileTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should set all user-provided metadata for a profile")
    void setAllUserMetadata() {
      Profile profile = new Profile("Foo");
      profile.setId("foo");
      profile.parent = "Observation";
      profile.title = "Foo Profile";
      profile.description = "foo bar foobar";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("Foo Profile", at(exported, "title"));
      assertEquals("foo bar foobar", at(exported, "description"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo", at(exported, "url"));
      assertEquals("Observation", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Observation", at(exported, "baseDefinition"));
      assertNull(at(exported, "version"));
      assertEquals("draft", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should set status and version metadata for a profile in FSHOnly mode")
    void setStatusAndVersionFshOnly() {
      useFshOnlyConfig();

      Profile profile = new Profile("Foo");
      profile.setId("foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("Observation", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Observation", at(exported, "baseDefinition"));
      assertEquals("0.1.0", at(exported, "version"));
      assertEquals("active", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should properly set/clear all metadata properties for a profile")
    void setClearAllMetadata() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);

      assertEquals("Foo", at(exported, "id")); // defaulted from user-provided name
      assertNull(at(exported, "meta"));
      assertNull(at(exported, "implicitRules"));
      assertNull(at(exported, "language"));
      assertNull(at(exported, "text"));
      assertNull(at(exported, "contained")); // inherited from Observation
      assertNull(at(exported, "extension")); // uninherited extensions are filtered out
      assertNull(at(exported, "modifierExtension"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url")); // constructed from canonical and id
      assertNull(at(exported, "identifier"));
      assertEquals("Foo", at(exported, "name")); // provided by user
      assertNull(at(exported, "title"));
      assertEquals("draft", at(exported, "status")); // always draft
      assertNull(at(exported, "version"));
      assertNull(at(exported, "experimental"));
      assertNull(at(exported, "date"));
      assertNull(at(exported, "publisher"));
      assertNull(at(exported, "contact"));
      assertNull(at(exported, "description"));
      assertNull(at(exported, "useContext"));
      assertNull(at(exported, "jurisdiction"));
      assertNull(at(exported, "purpose"));
      assertNull(at(exported, "copyright"));
      assertNull(at(exported, "keyword"));
      assertEquals("4.0.1", at(exported, "fhirVersion")); // Inherited from Observation
      assertJsonEqual("""
          [
            { "identity": "workflow", "uri": "http://hl7.org/fhir/workflow", "name": "Workflow Pattern" },
            {
              "identity": "sct-concept",
              "uri": "http://snomed.info/conceptdomain",
              "name": "SNOMED CT Concept Domain Binding"
            },
            { "identity": "v2", "uri": "http://hl7.org/v2", "name": "HL7 v2 Mapping" },
            { "identity": "rim", "uri": "http://hl7.org/v3", "name": "RIM Mapping" },
            { "identity": "w5", "uri": "http://hl7.org/fhir/fivews", "name": "FiveWs Pattern Mapping" },
            {
              "identity": "sct-attr",
              "uri": "http://snomed.org/attributebinding",
              "name": "SNOMED CT Attribute Binding"
            }
          ]""", at(exported, "mapping")); // inherited from Observation
      assertEquals("resource", at(exported, "kind")); // inherited from Observation
      assertEquals(false, at(exported, "abstract")); // always abstract
      assertNull(at(exported, "context")); // inherited from Observation
      assertNull(at(exported, "contextInvariant")); // inherited from Observation
      assertEquals("Observation", at(exported, "type")); // inherited from Observation
      assertEquals("http://hl7.org/fhir/StructureDefinition/Observation", at(exported, "baseDefinition")); // url for Observation
      assertEquals("constraint", at(exported, "derivation")); // always constraint
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should remove inherited top-level underscore-prefixed metadata properties for a profile")
    void removeInheritedUnderscoreProps() {
      Object jsonModifiedObservation = Js.cloneDeep(defs.fishForFHIR("Observation"));
      Common.setProp(jsonModifiedObservation, "id", "ModifiedObservation");
      Common.setProp(jsonModifiedObservation, "name", "ModifiedObservation");
      Common.setProp(jsonModifiedObservation, "url", "http://example.org/sd/ModifiedObservation");
      Common.setProp(jsonModifiedObservation, "_baseDefinition", json(CODEGEN_SUPER_BASE_DEFINITION));
      pkg.resources.add(StructureDefinition.fromJSON(jsonModifiedObservation));

      Profile profile = new Profile("Foo");
      profile.parent = "ModifiedObservation";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("http://example.org/sd/ModifiedObservation", at(exported, "baseDefinition")); // url for ModifiedObservation
      assertNull(at(exported, "_baseDefinition")); // should be stripped out
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should only inherit inheritable extensions for a profile")
    void onlyInheritInheritableExtensions() {
      Profile parent = new Profile("FooParent");
      parent.parent = "Observation";
      // Set a few uninheritable extensions directly on the SD
      CaretValueRule fmmRule = new CaretValueRule("");
      fmmRule.caretPath = "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm].valueInteger";
      fmmRule.value = 2.0;
      CaretValueRule wgRule = new CaretValueRule("");
      wgRule.caretPath = "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-wg].valueCode";
      wgRule.value = new FshCode("cds");
      // Set a few inheritable extensions directly on the SD
      CaretValueRule ancestorRule = new CaretValueRule("");
      ancestorRule.caretPath =
          "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-ancestor].valueUri";
      ancestorRule.value = "http://example.org/some/ancestor";
      CaretValueRule xmlNoOrderRule = new CaretValueRule("");
      xmlNoOrderRule.caretPath =
          "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-xml-no-order].valueBoolean";
      xmlNoOrderRule.value = true;
      // Set an inheritable extension on the Observation.code element
      CaretValueRule codeQuestionRule = new CaretValueRule("code");
      codeQuestionRule.caretPath =
          "extension[http://hl7.org/fhir/StructureDefinition/elementdefinition-question].valueString";
      codeQuestionRule.value = "What is the value?";
      // Set an uninheritable extension on the Observation.code element
      CaretValueRule codeFmmRule = new CaretValueRule("code");
      codeFmmRule.caretPath =
          "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status].valueCode";
      codeFmmRule.value = new FshCode("draft");

      parent.rules.addAll(List.of(fmmRule, wgRule, ancestorRule, xmlNoOrderRule, codeQuestionRule, codeFmmRule));
      doc.profiles.put(parent.name, parent);
      exporter.exportStructDef(parent);

      Profile profile = new Profile("Foo");
      profile.parent = "FooParent";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);

      StructureDefinition exportedParent = findById(pkg.profiles, "FooParent");
      assertNotNull(exportedParent);
      // The parent should have all the extensions it defined
      assertJsonEqual("""
          [
            { "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm", "valueInteger": 2 },
            { "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-wg", "valueCode": "cds" },
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-ancestor",
              "valueUri": "http://example.org/some/ancestor"
            },
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-xml-no-order",
              "valueBoolean": true
            }
          ]""", at(exportedParent, "extension"));
      ElementDefinition parentCodeElement = elementById(exportedParent, "Observation.code");
      assertJsonEqual("""
          [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-question",
              "valueString": "What is the value?"
            },
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status",
              "valueCode": "draft"
            }
          ]""", at(parentCodeElement, "extension"));

      StructureDefinition exported = findById(pkg.profiles, "Foo");
      assertNotNull(exported);
      // The following extensions should be stripped out as uninherited extensions:
      // { url: 'http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm', valueInteger: 2 },
      // { url: 'http://hl7.org/fhir/StructureDefinition/structuredefinition-wg', valueCode: 'cds' },
      //
      // BUT the following extensions should remain:
      assertJsonEqual("""
          [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-ancestor",
              "valueUri": "http://example.org/some/ancestor"
            },
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-xml-no-order",
              "valueBoolean": true
            }
          ]""", at(exported, "extension"));
      ElementDefinition exportedCodeElement = elementById(exported, "Observation.code");
      // The following extension should be stripped out as uninherited extension:
      // { url: 'http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status', valueCode: 'draft' },
      //
      // BUT the following extension should remain:
      assertJsonEqual("""
          [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/elementdefinition-question",
              "valueString": "What is the value?"
            }
          ]""", at(exportedCodeElement, "extension"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should not overwrite metadata that is not given for a profile")
    void notOverwriteMetadataNotGiven() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("Foo", at(exported, "id"));
      assertNull(at(exported, "title"));
      assertNull(at(exported, "description"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url"));
      assertEquals("Observation", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Observation", at(exported, "baseDefinition"));
      assertEquals("constraint", at(exported, "derivation"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should allow metadata to be overwritten with caret rule")
    void allowMetadataOverwriteWithCaretRule() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      CaretValueRule rule = new CaretValueRule("");
      rule.caretPath = "status";
      rule.value = new FshCode("draft");
      profile.rules.add(rule);
      doc.profiles.put(profile.name, profile);
      exporter.exportStructDef(profile);
      StructureDefinition exported = pkg.profiles.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("draft", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile should log an error when multiple profiles have the same id")
    void logErrorDuplicateProfileIds() {
      Profile firstProfile = new Profile("FirstProfile").withFile("Profiles.fsh").withLocation(2, 8, 6, 25);
      firstProfile.setId("my-profile");
      firstProfile.parent = "Basic";
      Profile secondProfile = new Profile("SecondProfile").withFile("Profiles.fsh").withLocation(8, 8, 11, 25);
      secondProfile.setId("my-profile");
      secondProfile.parent = "Basic";
      doc.profiles.put(firstProfile.name, firstProfile);
      doc.profiles.put(secondProfile.name, secondProfile);
      exporter.export();

      assertEquals(2, pkg.profiles.size());
      assertMatches("Multiple structure definitions with id my-profile", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Profiles\\.fsh.*Line: 8 - 11\\D*", "s", loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#Profile-Element")
  class ProfileElementTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile-Element should apply constraints to all instances of contentReference elements when the profile-element extension is applied")
    void applyConstraintsWithProfileElementExtension() {
      Profile profile = new Profile("TestQuestionnaire");
      profile.parent = "Questionnaire";
      profile.setId("example-q");

      ContainsRule containsRule = new ContainsRule("item.extension");
      containsRule.items = new ArrayList<>(List.of(
          new ContainsRuleItem("example-slice", "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl")));

      CardRule cardRule = new CardRule("item.extension[example-slice]");
      cardRule.min = 1.0;
      cardRule.max = "1";

      CaretValueRule profileRule = new CaretValueRule("item");
      profileRule.caretPath = "type.profile";
      profileRule.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/example-q";
      CaretValueRule extensionRule = new CaretValueRule("item");
      extensionRule.caretPath = "type.profile.extension.url";
      extensionRule.value = "http://hl7.org/fhir/StructureDefinition/elementdefinition-profile-element";
      CaretValueRule targetElementRule = new CaretValueRule("item");
      targetElementRule.caretPath = "type.profile.extension.valueString";
      targetElementRule.value = "Questionnaire.item";

      AssignmentRule assignmentRule = new AssignmentRule("item.item.linkId");
      assignmentRule.value = "item-2";

      profile.rules.addAll(
          List.of(containsRule, cardRule, profileRule, extensionRule, targetElementRule, assignmentRule));
      doc.profiles.put(profile.name, profile);

      // The slice added to the parent element should be unfolded to the child
      StructureDefinition exportedProfile = exporter.export().profiles.get(0);
      assertNotNull(exportedProfile.findElement("Questionnaire.item.item.extension:example-slice"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile-Element should apply the profile-element extension when there are several extensions in the type.profile array")
    void applyProfileElementExtensionAmongSeveral() {
      Profile profile = new Profile("TestQuestionnaire");
      profile.parent = "Questionnaire";
      profile.setId("example-q");

      ContainsRule containsRule = new ContainsRule("item.extension");
      containsRule.items = new ArrayList<>(List.of(
          new ContainsRuleItem("example-slice", "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl")));

      CardRule cardRule = new CardRule("item.extension[example-slice]");
      cardRule.min = 1.0;
      cardRule.max = "1";

      CaretValueRule fakeProfileRule = new CaretValueRule("item");
      fakeProfileRule.caretPath = "type.profile";
      fakeProfileRule.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/example-q";
      CaretValueRule fakeExtensionRule = new CaretValueRule("item");
      fakeExtensionRule.caretPath = "type.profile.extension.url";
      fakeExtensionRule.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/FakeExtension";
      CaretValueRule fakeTargetElementRule = new CaretValueRule("item");
      fakeTargetElementRule.caretPath = "type.profile.extension.valueString";
      fakeTargetElementRule.value = "Foo";

      CaretValueRule profileRule = new CaretValueRule("item");
      profileRule.caretPath = "type.profile[1]";
      profileRule.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/example-q";
      CaretValueRule extensionRule = new CaretValueRule("item");
      extensionRule.caretPath = "type.profile[1].extension.url";
      extensionRule.value = "http://hl7.org/fhir/StructureDefinition/elementdefinition-profile-element";
      CaretValueRule targetElementRule = new CaretValueRule("item");
      targetElementRule.caretPath = "type.profile[1].extension.valueString";
      targetElementRule.value = "Questionnaire.item";

      AssignmentRule assignmentRule = new AssignmentRule("item.item.linkId");
      assignmentRule.value = "item-2";

      profile.rules.addAll(List.of(containsRule, cardRule, fakeProfileRule, fakeExtensionRule, fakeTargetElementRule,
          profileRule, extensionRule, targetElementRule, assignmentRule));
      doc.profiles.put(profile.name, profile);

      // The slice added to the parent element should be unfolded to the child
      StructureDefinition exportedProfile = exporter.export().profiles.get(0);
      assertNotNull(exportedProfile.findElement("Questionnaire.item.item.extension:example-slice"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Profile-Element should not apply constraints to all instances of contentReference elements when the profile-element extension is misapplied")
    void notApplyConstraintsWhenProfileElementMisapplied() {
      Profile profile = new Profile("TestQuestionnaire2");
      profile.parent = "Questionnaire";
      profile.setId("example-q");

      ContainsRule containsRule = new ContainsRule("item.extension");
      containsRule.items = new ArrayList<>(List.of(
          new ContainsRuleItem("example-slice", "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl")));

      CardRule cardRule = new CardRule("item.extension[example-slice]");
      cardRule.min = 1.0;
      cardRule.max = "1";

      // The extension is targeting another profile
      CaretValueRule profileRule = new CaretValueRule("item");
      profileRule.caretPath = "type.profile";
      profileRule.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/wrong-id";
      CaretValueRule extensionRule = new CaretValueRule("item");
      extensionRule.caretPath = "type.profile.extension.url";
      extensionRule.value = "http://hl7.org/fhir/StructureDefinition/elementdefinition-profile-element";
      CaretValueRule targetElementRule = new CaretValueRule("item");
      targetElementRule.caretPath = "type.profile.extension.valueString";
      targetElementRule.value = "Questionnaire.item";

      AssignmentRule assignmentRule = new AssignmentRule("item.item.linkId");
      assignmentRule.value = "item-2";

      profile.rules.addAll(
          List.of(containsRule, cardRule, profileRule, extensionRule, targetElementRule, assignmentRule));
      doc.profiles.put(profile.name, profile);

      // The slice added to the parent element should be unfolded to the child
      StructureDefinition exportedProfile = exporter.export().profiles.get(0);
      assertNull(exportedProfile.findElement("Questionnaire.item.item.extension:example-slice"));
    }
  }

  @Nested
  @DisplayName("#Extension")
  class ExtensionTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should set all user-provided metadata for an extension")
    void setAllUserMetadata() {
      Extension extension = new Extension("Foo");
      extension.setId("foo");
      extension.title = "Foo Profile";
      extension.description = "foo bar foobar";
      extension.contexts = new ArrayList<>(List.of(
          new ExtensionContext("(Condition | Observation).code", true),
          new ExtensionContext("http://hl7.org/fhir/StructureDefinition/cqf-library", false),
          new ExtensionContext("Address.period.start", false)));
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("Foo Profile", at(exported, "title"));
      assertEquals("Foo Profile", at(exported.elements.get(0), "short"));
      assertEquals("foo bar foobar", at(exported, "description"));
      assertEquals("foo bar foobar", at(exported.elements.get(0), "definition"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo", at(exported, "url"));
      assertEquals("draft", at(exported, "status"));
      assertNull(at(exported, "version"));
      assertEquals("Extension", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Extension", at(exported, "baseDefinition"));
      assertJsonEqual("""
          [
            { "expression": "(Condition | Observation).code", "type": "fhirpath" },
            { "expression": "http://hl7.org/fhir/StructureDefinition/cqf-library", "type": "extension" },
            { "expression": "Address.period.start", "type": "element" }
          ]""", at(exported, "context"));

      // Check that Extension.url is correctly assigned
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo",
          at(elementById(exported, "Extension.url"), "fixedUri"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should set status and version metadata for an extension in FSHOnly mode")
    void setStatusAndVersionFshOnly() {
      useFshOnlyConfig();

      Extension extension = new Extension("Foo");
      extension.setId("foo");
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("0.1.0", at(exported, "version"));
      assertEquals("active", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should not set metadata on the root element when applyExtensionMetadataToRoot is false")
    void notSetRootMetadataWhenApplyExtensionMetadataToRootFalse() {
      Configuration config = minimalConfig();
      config.applyExtensionMetadataToRoot = false;
      FSHTank tank = new FSHTank(new ArrayList<>(List.of(doc)), config);
      StructureDefinitionExporter testExporter = new StructureDefinitionExporter(tank, pkg, fisher);
      Extension extension = new Extension("Foo");
      extension.setId("foo");
      extension.title = "Foo Profile";
      extension.description = "foo bar foobar";
      doc.extensions.put(extension.name, extension);
      testExporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("Foo Profile", at(exported, "title"));
      assertEquals("Optional Extensions Element", at(exported.elements.get(0), "short"));
      assertEquals("foo bar foobar", at(exported, "description"));
      assertEquals("Optional Extension Element - found in all resources.",
          at(exported.elements.get(0), "definition"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo", at(exported, "url"));
      assertEquals("Extension", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Extension", at(exported, "baseDefinition"));

      // Check that Extension.url is correctly assigned
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo",
          at(elementById(exported, "Extension.url"), "fixedUri"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should properly set/clear all metadata properties for an extension")
    void setClearAllMetadata() {
      Extension extension = new Extension("Foo");
      extension.parent = "patient-mothersMaidenName";
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);

      assertEquals("Foo", at(exported, "id")); // defaulted from user-provided name
      assertNull(at(exported, "meta"));
      assertNull(at(exported, "implicitRules"));
      assertNull(at(exported, "language"));
      assertNull(at(exported, "text"));
      assertNull(at(exported, "contained")); // inherited from patient-mothersMaidenName
      assertNull(at(exported, "extension")); // uninherited extensions are filtered out
      assertNull(at(exported, "modifierExtension"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url")); // constructed from canonical and id
      assertNull(at(exported, "identifier"));
      assertNull(at(exported, "version"));
      assertEquals("Foo", at(exported, "name")); // provided by user
      assertNull(at(exported, "title"));
      assertEquals("draft", at(exported, "status")); // always draft
      assertNull(at(exported, "experimental"));
      assertNull(at(exported, "date"));
      assertNull(at(exported, "publisher"));
      assertNull(at(exported, "contact"));
      assertNull(at(exported, "description"));
      assertNull(at(exported, "useContext"));
      assertNull(at(exported, "jurisdiction"));
      assertNull(at(exported, "purpose"));
      assertNull(at(exported, "copyright"));
      assertNull(at(exported, "keyword"));
      assertEquals("4.0.1", at(exported, "fhirVersion")); // Inherited from patient-mothersMaidenName
      assertJsonEqual("""
          [
            { "identity": "v2", "uri": "http://hl7.org/v2", "name": "HL7 v2 Mapping" },
            { "identity": "rim", "uri": "http://hl7.org/v3", "name": "RIM Mapping" }
          ]""", at(exported, "mapping")); // inherited from patient-mothersMaidenName
      assertEquals("complex-type", at(exported, "kind")); // inherited from patient-mothersMaidenName
      assertEquals(false, at(exported, "abstract")); // always abstract
      assertJsonEqual("""
          [{ "type": "element", "expression": "Patient" }]""", at(exported, "context")); // inherited from patient-mothersMaidenName
      assertNull(at(exported, "contextInvariant")); // inherited from patient-mothersMaidenName
      assertEquals("Extension", at(exported, "type")); // inherited from patient-mothersMaidenName
      assertEquals("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName",
          at(exported, "baseDefinition")); // url for patient-mothersMaidenName
      assertEquals("constraint", at(exported, "derivation")); // always constraint

      // Check that Extension.url is correctly assigned
      // Since the parent fixed this value, it should be the same as the parent
      assertEquals("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName",
          at(elementById(exported, "Extension.url"), "fixedUri"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should remove inherited top-level underscore-prefixed metadata properties for an extension")
    void removeInheritedUnderscoreProps() {
      Object jsonModifiedPatientMothersMaidenName = Js.cloneDeep(defs.fishForFHIR("patient-mothersMaidenName"));
      Common.setProp(jsonModifiedPatientMothersMaidenName, "id", "ModifiedPatientMothersMaidenName");
      Common.setProp(jsonModifiedPatientMothersMaidenName, "name", "ModifiedPatientMothersMaidenName");
      Common.setProp(jsonModifiedPatientMothersMaidenName, "url",
          "http://example.org/sd/ModifiedPatientMothersMaidenName");
      Common.setProp(jsonModifiedPatientMothersMaidenName, "_baseDefinition", json(CODEGEN_SUPER_BASE_DEFINITION));
      pkg.extensions.add(StructureDefinition.fromJSON(jsonModifiedPatientMothersMaidenName));

      Extension extension = new Extension("Foo");
      extension.parent = "ModifiedPatientMothersMaidenName";
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(1);
      assertEquals("http://example.org/sd/ModifiedPatientMothersMaidenName", at(exported, "baseDefinition")); // url for ModifiedPatientMothersMaidenName
      assertNull(at(exported, "_baseDefinition")); // should be stripped out
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should overwrite parent context when a new context is set")
    void overwriteParentContext() {
      Extension extension = new Extension("Foo");
      extension.parent = "patient-mothersMaidenName";
      extension.contexts = new ArrayList<>(List.of(new ExtensionContext("(Condition | Observation).code", true)));
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);
      assertJsonEqual("""
          [{ "expression": "(Condition | Observation).code", "type": "fhirpath" }]""", at(exported, "context"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should not overwrite metadata that is not given for an extension")
    void notOverwriteMetadataNotGiven() {
      Extension extension = new Extension("Foo");
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("Foo", at(exported, "id"));
      assertNull(at(exported, "title"));
      assertEquals("Optional Extensions Element", at(exported.elements.get(0), "short"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo",
          at(elementById(exported, "Extension.url"), "fixedUri"));
      assertNull(at(exported, "version"));
      assertJsonEqual("""
          [{ "type": "element", "expression": "Element" }]""", at(exported, "context"));
      assertEquals("Extension", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Extension", at(exported, "baseDefinition"));
      assertEquals("constraint", at(exported, "derivation"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should export sub-extensions, with similar starting names and different types")
    void exportSubExtensionsSimilarNames() {
      OnlyRule ruleString = new OnlyRule("extension[Foo].value[x]");
      ruleString.types = new ArrayList<>(List.of(new OnlyRuleType("string")));
      OnlyRule ruleDecimal = new OnlyRule("extension[FooBar].value[x]");
      ruleDecimal.types = new ArrayList<>(List.of(new OnlyRuleType("decimal")));
      Extension exParent = new Extension("Parent");

      CardRule FooFooCardRule = new CardRule("extension[Foo]");
      FooFooCardRule.min = 1.0;
      FooFooCardRule.max = "1"; // * extension[sliceB].extension 1..1

      CardRule FooBarCardRule = new CardRule("extension[FooBar]");
      FooBarCardRule.min = 0.0;
      FooBarCardRule.max = "1"; // * extension[sliceB].extension 0..0

      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(List.of(new ContainsRuleItem("Foo"), new ContainsRuleItem("FooBar")));

      exParent.rules.addAll(List.of(containsRule, FooFooCardRule, FooBarCardRule, ruleString, ruleDecimal));

      exporter.exportStructDef(exParent);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition extension = elementById(sd, "Extension.extension:FooBar.value[x]");
      assertEquals("decimal", extension.getType().get(0).getCode());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should not hardcode in the default context if parent already had a context")
    void notHardcodeDefaultContext() {
      Extension extension = new Extension("Foo");
      extension.parent = "http://hl7.org/fhir/StructureDefinition/patient-animal";
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);
      assertJsonEqual("""
          [{ "type": "element", "expression": "Patient" }]""", at(exported, "context"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should allow metadata to be overwritten with caret rule")
    void allowMetadataOverwriteWithCaretRule() {
      Extension extension = new Extension("Foo");
      CaretValueRule rule1 = new CaretValueRule("");
      rule1.caretPath = "status";
      rule1.value = new FshCode("draft");
      CaretValueRule rule2 = new CaretValueRule("");
      rule2.caretPath = "context[0].type";
      rule2.value = new FshCode("element");
      CaretValueRule rule3 = new CaretValueRule("");
      rule3.caretPath = "context[0].expression";
      rule3.value = "Observation";
      extension.rules.addAll(List.of(rule1, rule2, rule3));
      doc.extensions.put(extension.name, extension);
      exporter.exportStructDef(extension);
      StructureDefinition exported = pkg.extensions.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("draft", at(exported, "status"));
      assertJsonEqual("""
          [{ "type": "element", "expression": "Observation" }]""", at(exported, "context"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should log an error when multiple extensions have the same id")
    void logErrorDuplicateExtensionIds() {
      Extension firstExtension = new Extension("FirstExtension").withFile("Extensions.fsh").withLocation(3, 8, 7, 22);
      firstExtension.setId("my-extension");
      Extension secondExtension =
          new Extension("SecondExtension").withFile("Extensions.fsh").withLocation(11, 8, 15, 29);
      secondExtension.setId("my-extension");
      doc.extensions.put(firstExtension.name, firstExtension);
      doc.extensions.put(secondExtension.name, secondExtension);

      exporter.export();
      assertEquals(2, pkg.extensions.size());
      assertMatches("Multiple structure definitions with id my-extension", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Extensions\\.fsh.*Line: 11 - 15\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Extension should log an error when a profile and an extension have the same id")
    void logErrorProfileAndExtensionSameId() {
      Profile profile = new Profile("MyProfile").withFile("Profiles.fsh").withLocation(2, 8, 5, 15);
      profile.setId("custom-definition");
      profile.parent = "Basic";
      Extension extension = new Extension("MyExtension").withFile("Extensions.fsh").withLocation(3, 8, 5, 19);
      extension.setId("custom-definition");
      doc.profiles.put(profile.name, profile);
      doc.extensions.put(extension.name, extension);

      exporter.export();
      assertEquals(1, pkg.profiles.size());
      assertEquals(1, pkg.extensions.size());
      assertMatches("Multiple structure definitions with id custom-definition", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: Extensions\\.fsh.*Line: 3 - 5\\D*", "s", loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#LogicalModel")
  class LogicalModelTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should have the correct baseDefinition of Base when parent is not provided")
    void baseDefinitionOfBaseWithoutParent() {
      Logical logical = new Logical("Foo");
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should have the correct baseDefinition for a provided parent")
    void baseDefinitionForProvidedParent() {
      Logical logical = new Logical("Foo");
      logical.parent = "AlternateIdentification";
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/cda/StructureDefinition/AlternateIdentification",
          at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should set all user-provided metadata for a logical model")
    void setAllUserMetadata() {
      Logical logical = new Logical("Foo");
      logical.setId("foo");
      logical.title = "Logical Foo Model";
      logical.description = "foo bar foobar";
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("Logical Foo Model", at(exported, "title"));
      assertEquals("foo bar foobar", at(exported, "description"));
      assertEquals("draft", at(exported, "status"));
      assertNull(at(exported, "version"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo", at(exported, "url"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should set status and version metadata for a logical model in FSHOnly mode")
    void setStatusAndVersionFshOnly() {
      useFshOnlyConfig();

      Logical logical = new Logical("Foo");
      logical.setId("foo");
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("0.1.0", at(exported, "version"));
      assertEquals("active", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should properly set/clear all metadata properties for a logical model")
    void setClearAllMetadata() {
      Logical logical = new Logical("Foo");
      logical.parent = "AlternateIdentification";
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("Foo", at(exported, "id")); // defaulted from user-provided name
      assertNull(at(exported, "meta"));
      assertNull(at(exported, "implicitRules"));
      assertNull(at(exported, "language"));
      assertNull(at(exported, "text"));
      assertNull(at(exported, "contained"));
      assertNull(at(exported, "modifierExtension"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url")); // constructed from canonical and id
      assertNull(at(exported, "identifier"));
      assertNull(at(exported, "version"));
      assertEquals("Foo", at(exported, "name")); // provided by user
      assertNull(at(exported, "title"));
      assertEquals("draft", at(exported, "status")); // always draft
      assertNull(at(exported, "experimental"));
      assertNull(at(exported, "date"));
      assertNull(at(exported, "publisher"));
      assertNull(at(exported, "contact"));
      assertNull(at(exported, "description"));
      assertNull(at(exported, "useContext"));
      assertNull(at(exported, "jurisdiction"));
      assertNull(at(exported, "purpose"));
      assertNull(at(exported, "copyright"));
      assertNull(at(exported, "keyword"));
      assertEquals("4.0.1", at(exported, "fhirVersion")); // Inherited from AlternateIdentification
      assertNull(at(exported, "mapping")); // inherited from AlternateIdentification
      assertEquals("logical", at(exported, "kind")); // inherited from AlternateIdentification
      assertEquals(false, at(exported, "abstract")); // always abstract
      assertNull(at(exported, "context")); // inherited from AlternateIdentification
      assertNull(at(exported, "contextInvariant")); // inherited from AlternateIdentification
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/cda/StructureDefinition/AlternateIdentification",
          at(exported, "baseDefinition")); // url for AlternateIdentification
      assertEquals("specialization", at(exported, "derivation")); // always specialization for logical models
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should remove inherited top-level underscore-prefixed metadata properties for a logical model")
    void removeInheritedUnderscoreProps() {
      Object jsonModifiedAltID = Js.cloneDeep(defs.fishForFHIR("AlternateIdentification"));
      Common.setProp(jsonModifiedAltID, "id", "ModifiedAlternateIdentification");
      Common.setProp(jsonModifiedAltID, "name", "ModifiedAlternateIdentification");
      Common.setProp(jsonModifiedAltID, "url", "http://example.org/sd/ModifiedAlternateIdentification");
      Common.setProp(jsonModifiedAltID, "_baseDefinition", json(CODEGEN_SUPER_BASE_DEFINITION));
      pkg.logicals.add(StructureDefinition.fromJSON(jsonModifiedAltID));

      Logical logical = new Logical("Foo");
      logical.parent = "ModifiedAlternateIdentification";
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(1);
      assertEquals("http://example.org/sd/ModifiedAlternateIdentification", at(exported, "baseDefinition")); // url for ModifiedObservation
      assertNull(at(exported, "_baseDefinition")); // should be stripped out
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should not overwrite metadata that is not given for a logical model")
    void notOverwriteMetadataNotGiven() {
      Logical logical = new Logical("Foo");
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("Foo", at(exported, "id"));
      assertNull(at(exported, "title"));
      assertNull(at(exported, "description"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported, "baseDefinition"));
      assertEquals("specialization", at(exported, "derivation"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should allow metadata to be overwritten with caret rule")
    void allowMetadataOverwriteWithCaretRule() {
      Logical logical = new Logical("Foo");
      logical.parent = "AlternateIdentification";
      CaretValueRule rule = new CaretValueRule("");
      rule.caretPath = "status";
      rule.value = new FshCode("draft");
      logical.rules.add(rule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("draft", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should allow type to be overwritten with caret rule with a uri value")
    void allowTypeOverwriteWithUri() {
      Logical logical = new Logical("Foo");
      logical.parent = "AlternateIdentification";
      CaretValueRule rule = new CaretValueRule("");
      rule.caretPath = "type";
      rule.value = "http://example.org/other/MyType";
      logical.rules.add(rule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("http://example.org/other/MyType", at(exported, "type"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should log a warning and allow overwriting type with caret rule with a non-uri value")
    void warnAndAllowTypeOverwriteWithNonUri() {
      Logical logical = new Logical("Foo");
      logical.parent = "AlternateIdentification";
      CaretValueRule rule = new CaretValueRule("").withFile("LogicalModels.fsh").withLocation(3, 3, 3, 25);
      rule.caretPath = "type";
      rule.value = "MyType";
      logical.rules.add(rule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("MyType", at(exported, "type"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("MyType is an invalid type\\. Logical models require that the type be an absolute URL\\.", "s",
          loggerSpy.getLastMessage("warn"));
      assertMatches("File: LogicalModels\\.fsh.*Line: 3", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should log an error when multiple logical models have the same id")
    void logErrorDuplicateLogicalIds() {
      Logical firstLogicalModel =
          new Logical("FirstLogicalModel").withFile("LogicalModels.fsh").withLocation(2, 8, 6, 25);
      firstLogicalModel.setId("my-model");
      Logical secondLogicalModel =
          new Logical("SecondLogicalModel").withFile("LogicalModels.fsh").withLocation(8, 8, 11, 25);
      secondLogicalModel.setId("my-model");
      doc.logicals.put(firstLogicalModel.name, firstLogicalModel);
      doc.logicals.put(secondLogicalModel.name, secondLogicalModel);

      exporter.export();
      assertEquals(2, pkg.logicals.size());
      assertMatches("Multiple structure definitions with id my-model", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: LogicalModels\\.fsh.*Line: 8 - 11\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should log an error when a profile and a logical model have the same id")
    void logErrorProfileAndLogicalSameId() {
      Profile profile = new Profile("MyProfile").withFile("Profiles.fsh").withLocation(2, 8, 5, 15);
      profile.setId("custom-definition");
      profile.parent = "Basic";
      Logical logical = new Logical("MyModel").withFile("Logicals.fsh").withLocation(3, 8, 5, 19);
      logical.setId("custom-definition");
      doc.profiles.put(profile.name, profile);
      doc.logicals.put(logical.name, logical);

      exporter.export();
      assertEquals(1, pkg.profiles.size());
      assertEquals(1, pkg.logicals.size());
      assertMatches("Multiple structure definitions with id custom-definition", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: Logicals\\.fsh.*Line: 3 - 5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should include added elements along with parent elements")
    void includeAddedElementsWithParentElements() {
      Logical logical = new Logical("MyTestModel");
      logical.parent = "Base";
      logical.setId("MyModel");

      AddElementRule addElementRule1 = new AddElementRule("prop1");
      addElementRule1.min = 0.0;
      addElementRule1.max = "1";
      addElementRule1.types = new ArrayList<>(List.of(new OnlyRuleType("dateTime")));
      addElementRule1.short_ = "short of property1";
      logical.rules.add(addElementRule1);

      AddElementRule addElementRule2 = new AddElementRule("prop2");
      addElementRule2.min = 0.0;
      addElementRule2.max = "*";
      addElementRule2.summary = true;
      addElementRule2.types = new ArrayList<>(List.of(new OnlyRuleType("string")));
      addElementRule2.short_ = "short of property2";
      logical.rules.add(addElementRule2);

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("MyTestModel", at(exported, "name"));
      assertEquals("MyModel", at(exported, "id"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(exported, "type"));
      assertEquals(3, exported.elements.size()); // 1 Base element + 2 added elements
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should include added elements for BackboneElement and children")
    void includeAddedBackboneElementAndChildren() {
      Logical logical = new Logical("MyTestModel");
      logical.parent = "Element";
      logical.setId("MyModel");

      AddElementRule addElementRule1 = new AddElementRule("backboneProp");
      addElementRule1.min = 0.0;
      addElementRule1.max = "*";
      addElementRule1.types = new ArrayList<>(List.of(new OnlyRuleType("BackboneElement")));
      addElementRule1.short_ = "short of backboneProp";
      logical.rules.add(addElementRule1);

      AddElementRule addElementRule2 = new AddElementRule("backboneProp.name");
      addElementRule2.min = 1.0;
      addElementRule2.max = "1";
      addElementRule2.types = new ArrayList<>(List.of(new OnlyRuleType("HumanName")));
      addElementRule2.short_ = "short of backboneProp.name";
      logical.rules.add(addElementRule2);

      AddElementRule addElementRule3 = new AddElementRule("backboneProp.address");
      addElementRule3.min = 0.0;
      addElementRule3.max = "*";
      addElementRule3.types = new ArrayList<>(List.of(new OnlyRuleType("Address")));
      addElementRule3.short_ = "short of backboneProp.address";
      logical.rules.add(addElementRule3);

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("MyTestModel", at(exported, "name"));
      assertEquals("MyModel", at(exported, "id"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(exported, "type"));
      assertEquals(6, exported.elements.size()); // 3 Element elements + 3 added elements

      ElementDefinition prop1 = exported.findElement("MyModel.backboneProp");
      assertEquals("MyModel.backboneProp", at(prop1, "path"));
      ElementDefinitionType expectedType1 = new ElementDefinitionType("BackboneElement");
      assertJsEqual(List.of(expectedType1), prop1.getType());

      ElementDefinition prop2 = exported.findElement("MyModel.backboneProp.name");
      assertEquals("MyModel.backboneProp.name", at(prop2, "path"));
      ElementDefinitionType expectedType2 = new ElementDefinitionType("HumanName");
      assertJsEqual(List.of(expectedType2), prop2.getType());

      ElementDefinition prop3 = exported.findElement("MyModel.backboneProp.address");
      assertEquals("MyModel.backboneProp.address", at(prop3, "path"));
      ElementDefinitionType expectedType3 = new ElementDefinitionType("Address");
      assertJsEqual(List.of(expectedType3), prop3.getType());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #LogicalModel should log an error when MustSupport is true in a logical model")
    void logErrorMustSupportInLogical() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule =
          new AddElementRule("prop1").withFile("MustSupportFlag.fsh").withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(new OnlyRuleType("string")));
      addElementRule.short_ = "short definition";
      addElementRule.mustSupport = true;
      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      exported.findElement("MyModel.prop1");
      assertMatches("File: MustSupportFlag\\.fsh.*Line: 3 - 8\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("The MustSupport flag is not permitted", "s", loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#Resource")
  class ResourceTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should have the correct baseDefinition of Element when parent is not provided")
    void baseDefinitionWithoutParent() {
      Resource resource = new Resource("Foo");
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should have the correct baseDefinition for a Resource parent")
    void baseDefinitionForResourceParent() {
      Resource resource = new Resource("Foo");
      resource.parent = "Resource";
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Resource", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should have the correct baseDefinition for a DomainResource parent")
    void baseDefinitionForDomainResourceParent() {
      Resource resource = new Resource("Foo");
      resource.parent = "DomainResource";
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should set all user-provided metadata for a resource")
    void setAllUserMetadata() {
      Resource resource = new Resource("Foo");
      resource.setId("foo");
      resource.title = "Custom Foo Resource";
      resource.description = "foo bar foobar";
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("Custom Foo Resource", at(exported, "title"));
      assertEquals("foo bar foobar", at(exported, "description"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/foo", at(exported, "url"));
      assertEquals("draft", at(exported, "status"));
      assertNull(at(exported, "version"));
      assertEquals("foo", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported, "baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should set status and version metadata for a resource in FSHOnly mode")
    void setStatusAndVersionFshOnly() {
      useFshOnlyConfig();

      Resource resource = new Resource("Foo");
      resource.setId("foo");
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("foo", at(exported, "id"));
      assertEquals("0.1.0", at(exported, "version"));
      assertEquals("active", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should properly set/clear all metadata properties for a resource")
    void setClearAllMetadata() {
      Resource resource = new Resource("Foo");
      resource.parent = "Resource";
      resource.title = "Custom Foo Resource";
      resource.description = "foo bar foobar";
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("Foo", at(exported, "id")); // defaulted from user-provided name
      assertEquals("Custom Foo Resource", at(exported, "title"));
      assertNull(at(exported, "meta"));
      assertNull(at(exported, "implicitRules"));
      assertNull(at(exported, "language"));
      assertNull(at(exported, "text"));
      assertNull(at(exported, "contained"));
      assertNull(at(exported, "modifierExtension"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url")); // constructed from canonical and id
      assertNull(at(exported, "identifier"));
      assertNull(at(exported, "version"));
      assertEquals("Foo", at(exported, "name")); // provided by user
      assertEquals("draft", at(exported, "status")); // always draft
      assertNull(at(exported, "experimental"));
      assertNull(at(exported, "date"));
      assertNull(at(exported, "publisher"));
      assertNull(at(exported, "contact"));
      assertEquals("foo bar foobar", at(exported, "description"));
      assertNull(at(exported, "useContext"));
      assertNull(at(exported, "jurisdiction"));
      assertNull(at(exported, "purpose"));
      assertNull(at(exported, "copyright"));
      assertNull(at(exported, "keyword"));
      assertEquals("4.0.1", at(exported, "fhirVersion")); // Inherited from Resource
      // TS: const mapping: StructureDefinitionMapping = { identity: 'rim', uri: 'http://hl7.org/v3', name: 'RIM Mapping' }
      assertJsonEqual("""
          [{ "identity": "rim", "uri": "http://hl7.org/v3", "name": "RIM Mapping" }]""",
          at(exported, "mapping")); // inherited from Resource
      assertEquals("resource", at(exported, "kind")); // inherited from Resource
      assertEquals(false, at(exported, "abstract")); // always abstract
      assertNull(at(exported, "context")); // inherited from Resource
      assertNull(at(exported, "contextInvariant")); // inherited from Resource
      assertEquals("Foo", at(exported, "type")); // inherited from Resource
      assertEquals("http://hl7.org/fhir/StructureDefinition/Resource", at(exported, "baseDefinition")); // url for Resource
      assertEquals("specialization", at(exported, "derivation")); // always specialization for resource
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should remove inherited top-level underscore-prefixed metadata properties for a resource")
    void removeInheritedUnderscoreProps() {
      Object jsonModifiedResource = Js.cloneDeep(defs.fishForFHIR("Resource"));
      Common.setProp(jsonModifiedResource, "id", "ModifiedResource");
      Common.setProp(jsonModifiedResource, "name", "ModifiedResource");
      Common.setProp(jsonModifiedResource, "url", "http://example.org/sd/ModifiedResource");
      Common.setProp(jsonModifiedResource, "_baseDefinition", json(CODEGEN_SUPER_BASE_DEFINITION));
      pkg.resources.add(StructureDefinition.fromJSON(jsonModifiedResource));

      Resource resource = new Resource("Foo");
      resource.parent = "ModifiedResource";
      resource.title = "Custom Foo Resource";
      resource.description = "foo bar foobar";
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(1);
      assertEquals("http://example.org/sd/ModifiedResource", at(exported, "baseDefinition")); // url for ModifiedObservation
      assertNull(at(exported, "_baseDefinition")); // should be stripped out
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should not overwrite metadata that is not given for a resource")
    void notOverwriteMetadataNotGiven() {
      Resource resource = new Resource("Foo");
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("Foo", at(exported, "name"));
      assertEquals("Foo", at(exported, "id"));
      assertNull(at(exported, "title"));
      assertNull(at(exported, "description"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(exported, "url"));
      assertNull(at(exported, "version"));
      assertEquals("Foo", at(exported, "type"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported, "baseDefinition"));
      assertEquals("specialization", at(exported, "derivation"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should allow metadata to be overwritten with caret rule")
    void allowMetadataOverwriteWithCaretRule() {
      Resource resource = new Resource("Foo");
      doc.resources.put(resource.name, resource);
      CaretValueRule rule = new CaretValueRule("");
      rule.caretPath = "status";
      rule.value = new FshCode("draft");
      resource.rules.add(rule);
      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);
      assertEquals("Foo", at(exported, "name"));
      assertEquals("draft", at(exported, "status"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should log an error when multiple resources have the same id")
    void logErrorDuplicateResourceIds() {
      Resource firstResource = new Resource("FirstResource").withFile("Resources.fsh").withLocation(2, 8, 6, 25);
      firstResource.setId("my-resource");
      Resource secondResource = new Resource("SecondResource").withFile("Resources.fsh").withLocation(8, 8, 11, 25);
      secondResource.setId("my-resource");
      doc.resources.put(firstResource.name, firstResource);
      doc.resources.put(secondResource.name, secondResource);

      exporter.export();
      assertEquals(2, pkg.resources.size());
      assertMatches("Multiple structure definitions with id my-resource", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Resources\\.fsh.*Line: 8 - 11\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should log an error when a resource and a logical model have the same id")
    void logErrorResourceAndLogicalSameId() {
      Resource resource = new Resource("MyResource").withFile("Resource.fsh").withLocation(2, 8, 5, 15);
      resource.setId("custom-id");
      doc.resources.put(resource.name, resource);

      Logical logical = new Logical("MyModel").withFile("Logicals.fsh").withLocation(3, 8, 5, 19);
      logical.setId("custom-id");
      doc.logicals.put(logical.name, logical);

      exporter.export();
      assertEquals(1, pkg.resources.size());
      assertEquals(1, pkg.logicals.size());
      assertMatches("Multiple structure definitions with id custom-id", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Resource\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should include added elements along with parent root element")
    void includeAddedElementsWithParentRootElement() {
      Resource resource = new Resource("MyTestResource");
      resource.parent = "DomainResource";
      resource.setId("MyResource");

      AddElementRule addElementRule1 = new AddElementRule("prop1");
      addElementRule1.min = 0.0;
      addElementRule1.max = "1";
      addElementRule1.types = new ArrayList<>(List.of(new OnlyRuleType("dateTime")));
      addElementRule1.short_ = "short of property1";
      resource.rules.add(addElementRule1);

      AddElementRule addElementRule2 = new AddElementRule("prop2");
      addElementRule2.min = 0.0;
      addElementRule2.max = "*";
      addElementRule2.summary = true;
      addElementRule2.types = new ArrayList<>(List.of(new OnlyRuleType("string")));
      addElementRule2.short_ = "short of property2";
      resource.rules.add(addElementRule2);

      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("MyTestResource", at(exported, "name"));
      assertEquals("MyResource", at(exported, "id"));
      assertEquals("MyResource", at(exported, "type"));
      assertEquals(11, exported.elements.size()); // 9 - DomainResource elements + 2 AddElementRules
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should include added elements for BackboneElement and children")
    void includeAddedBackboneElementAndChildren() {
      Resource resource = new Resource("MyTestResource");
      resource.parent = "Resource";
      resource.setId("MyResource");

      AddElementRule addElementRule1 = new AddElementRule("backboneProp");
      addElementRule1.min = 0.0;
      addElementRule1.max = "*";
      addElementRule1.types = new ArrayList<>(List.of(new OnlyRuleType("BackboneElement")));
      addElementRule1.short_ = "short of backboneProp";
      resource.rules.add(addElementRule1);

      AddElementRule addElementRule2 = new AddElementRule("backboneProp.name");
      addElementRule2.min = 1.0;
      addElementRule2.max = "1";
      addElementRule2.types = new ArrayList<>(List.of(new OnlyRuleType("HumanName")));
      addElementRule2.short_ = "short of backboneProp.name";
      resource.rules.add(addElementRule2);

      AddElementRule addElementRule3 = new AddElementRule("backboneProp.address");
      addElementRule3.min = 0.0;
      addElementRule3.max = "*";
      addElementRule3.types = new ArrayList<>(List.of(new OnlyRuleType("Address")));
      addElementRule3.short_ = "short of backboneProp.address";
      resource.rules.add(addElementRule3);

      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("MyTestResource", at(exported, "name"));
      assertEquals("MyResource", at(exported, "id"));
      assertEquals("MyResource", at(exported, "type"));
      assertEquals(8, exported.elements.size()); // 5 - Resource elements + 3 AddElementRules

      ElementDefinition prop1 = exported.findElement("MyResource.backboneProp");
      assertEquals("MyResource.backboneProp", at(prop1, "path"));
      ElementDefinitionType expectedType1 = new ElementDefinitionType("BackboneElement");
      assertJsEqual(List.of(expectedType1), prop1.getType());

      ElementDefinition prop2 = exported.findElement("MyResource.backboneProp.name");
      assertEquals("MyResource.backboneProp.name", at(prop2, "path"));
      ElementDefinitionType expectedType2 = new ElementDefinitionType("HumanName");
      assertJsEqual(List.of(expectedType2), prop2.getType());

      ElementDefinition prop3 = exported.findElement("MyResource.backboneProp.address");
      assertEquals("MyResource.backboneProp.address", at(prop3, "path"));
      ElementDefinitionType expectedType3 = new ElementDefinitionType("Address");
      assertJsEqual(List.of(expectedType3), prop3.getType());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Resource should log an error when MustSupport is true in a resource")
    void logErrorMustSupportInResource() {
      Resource resource = new Resource("MyTestResource");
      resource.setId("MyResource");

      AddElementRule addElementRule =
          new AddElementRule("prop1").withFile("MustSupportFlag.fsh").withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = new ArrayList<>(List.of(new OnlyRuleType("string")));
      addElementRule.short_ = "short definition";
      addElementRule.mustSupport = true;
      resource.rules.add(addElementRule);
      doc.resources.put(resource.name, resource);

      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals(10, exported.elements.size());

      exported.findElement("MyResource.prop1");
      assertMatches("File: MustSupportFlag\\.fsh.*Line: 3 - 8\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("The MustSupport flag is not permitted", "s", loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#Invariant")
  class InvariantTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should not warn or error on a valid Invariant using keywords")
    void validInvariantUsingKeywords() {
      Invariant invariant = new Invariant("MyInvariant");
      invariant.description = "My important invariant";
      invariant.severity = new FshCode("warning");
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should not warn or error on a valid Invariant using rules")
    void validInvariantUsingRules() {
      Invariant invariant = new Invariant("MyInvariant");
      AssignmentRule humanRule = new AssignmentRule("human");
      humanRule.value = "My important invariant";
      AssignmentRule severityRule = new AssignmentRule("severity");
      severityRule.value = new FshCode("warning");
      invariant.rules = new ArrayList<>(List.of(humanRule, severityRule));
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should log an error when description is not provided")
    void logErrorMissingDescription() {
      Invariant invariant = new Invariant("MyInvariant").withFile("BadInvariant.fsh").withLocation(4, 0, 5, 17);
      invariant.severity = new FshCode("warning");
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertMatches(
          "Invariant MyInvariant is missing its human description\\. To set the description, add the \"Description:\" keyword or add a rule assigning \"human\" to a string value\\.",
          "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: BadInvariant\\.fsh.*Line: 4\\D+5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should log an error when severity is not provided")
    void logErrorMissingSeverity() {
      Invariant invariant = new Invariant("MyInvariant").withFile("BadInvariant.fsh").withLocation(4, 0, 5, 34);
      invariant.description = "My important invariant";
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertMatches(
          "Invariant MyInvariant is missing its severity level\\. To set the severity, add the \"Severity:\" keyword or add a rule assigning \"severity\" to #error or #warning\\.",
          "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: BadInvariant\\.fsh.*Line: 4\\D+5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should log an error when severity is not one of the valid values (set by keyword)")
    void logErrorInvalidSeverityKeyword() {
      Invariant invariant = new Invariant("MyInvariant").withFile("BadInvariant.fsh").withLocation(4, 0, 6, 23);
      invariant.description = "My important invariant";
      invariant.severity = new FshCode("nice-to-have").withFile("BadInvariant.fsh").withLocation(6, 10, 6, 23);
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertMatches(
          "Invariant MyInvariant has an invalid severity level\\. Supported values are #error and #warning\\.", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: BadInvariant\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should log an error when severity is not one of the valid values (set by rule)")
    void logErrorInvalidSeverityRule() {
      Invariant invariant = new Invariant("MyInvariant").withFile("BadInvariant.fsh").withLocation(4, 0, 6, 27);
      invariant.description = "My important invariant";
      AssignmentRule severityRule = new AssignmentRule("severity");
      severityRule.value = new FshCode("nice-to-have").withFile("BadInvariant.fsh").withLocation(6, 14, 6, 27);
      invariant.rules = new ArrayList<>(List.of(severityRule));
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertMatches(
          "Invariant MyInvariant has an invalid severity level\\. Supported values are #error and #warning\\.", "s",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: BadInvariant\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should log a warning when severity includes a system (set by keyword)")
    void warnSeverityWithSystemKeyword() {
      Invariant invariant = new Invariant("MyInvariant").withFile("BadInvariant.fsh").withLocation(4, 0, 6, 46);
      invariant.description = "My important invariant";
      invariant.severity = new FshCode("error", "http://hl7.org/fhir/constraint-severity")
          .withFile("BadInvariant.fsh")
          .withLocation(6, 10, 6, 46);
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertMatches(
          "Invariant MyInvariant has a severity level including a code system\\. Remove the code system from the value\\.",
          "s", loggerSpy.getLastMessage("warn"));
      assertMatches("File: BadInvariant\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Invariant should log a warning when severity includes a system (set by rule)")
    void warnSeverityWithSystemRule() {
      Invariant invariant = new Invariant("MyInvariant").withFile("BadInvariant.fsh").withLocation(4, 0, 6, 50);
      invariant.description = "My important invariant";
      AssignmentRule severityRule = new AssignmentRule("severity");
      severityRule.value = new FshCode("error", "http://hl7.org/fhir/constraint-severity")
          .withFile("BadInvariant.fsh")
          .withLocation(6, 14, 6, 50);
      invariant.rules = new ArrayList<>(List.of(severityRule));
      doc.invariants.put(invariant.name, invariant);

      exporter.export();
      assertMatches(
          "Invariant MyInvariant has a severity level including a code system. Remove the code system from the value\\.",
          "s", loggerSpy.getLastMessage("warn"));
      assertMatches("File: BadInvariant\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }
  }

  @Nested
  @DisplayName("#Rules")
  class RulesTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #Rules should emit an error and continue when the path is not found")
    void emitErrorAndContinuePathNotFound() {
      Profile profile = new Profile("Foo");
      profile.parent = "Basic";
      CardRule rule = new CardRule("fakePath").withFile("Foo.fsh").withLocation(3, 8, 4, 22);
      rule.min = 0.0;
      rule.max = "1";
      profile.rules.add(rule);
      exporter.exportStructDef(profile);
      StructureDefinition structDef = pkg.profiles.get(0);
      assertNotNull(structDef);
      assertEquals("Basic", at(structDef, "type"));
      assertMatches("File: Foo\\.fsh.*Line: 3 - 4\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #Rules should emit an error and continue when the path for the child of a choice element is not found")
    void emitErrorAndContinueChoiceChildNotFound() {
      Profile profile = new Profile("Foo");
      profile.parent = "Basic";
      AssignmentRule rule = new AssignmentRule("value[x].comparator").withFile("Foo.fsh").withLocation(4, 7, 4, 22);
      rule.value = new FshCode(">=");
      profile.rules.add(rule);
      exporter.exportStructDef(profile);
      StructureDefinition structDef = pkg.profiles.get(0);
      assertNotNull(structDef);
      assertEquals("Basic", at(structDef, "type"));
      assertMatches("File: Foo\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
    }
  }
}
