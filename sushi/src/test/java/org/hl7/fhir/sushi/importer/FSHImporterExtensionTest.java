package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.testhelpers.Asserts.*;
import static org.hl7.fhir.sushi.testhelpers.ImportSingleText.importSingleText;
import static org.hl7.fhir.sushi.testhelpers.LeftAlign.leftAlign;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/FSHImporter.Extension.test.ts (SUSHI 3.21.1)
class FSHImporterExtensionTest {
  /**
   * TS has no reset here: Jest runs the file's tests in source order, so the "no errors" assertions only see the
   * tests before them. JUnit orders methods differently, so start each test with a clean spy (same intent).
   */
  @BeforeEach
  void resetLoggerSpy() {
    loggerSpy.reset();
  }

  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @Nested
    @DisplayName("Extension")
    class ExtensionTests {
      @Nested
      @DisplayName("#sdMetadata")
      class SdMetadataTests {
        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should parse the simplest possible extension")
        void parseSimplestPossibleExtension() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.extensions.size());
          var extension = result.extensions.get("SomeExtension");
          assertEquals("SomeExtension", extension.name);
          // if no parent is explicitly set, should default to Extension
          assertEquals("Extension", extension.parent);
          // if no id is explicitly set, should default to name
          assertEquals("SomeExtension", extension.getId());
          assertToEqual(new TextLocation(2, 9, 2, 32), extension.sourceInfo.location);
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should parse profile with additional metadata properties")
        void parseProfileAdditionalMetadataProperties() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Parent: ParentExtension\n"
                           + "        Id: some-extension\n"
                           + "        Title: \"Some Extension\"\n"
                           + "        Description: \"An extension on something\"\n"
                           + "        Context: \"some.fhirpath()\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.extensions.size());
          var extension = result.extensions.get("SomeExtension");
          assertEquals("SomeExtension", extension.name);
          assertEquals("ParentExtension", extension.parent);
          assertEquals("some-extension", extension.getId());
          assertEquals("Some Extension", extension.title);
          assertEquals("An extension on something", extension.description);
          assertToEqual(List.of(new org.hl7.fhir.sushi.fshtypes.ExtensionContext("some.fhirpath()", true, new SourceInfo("", new TextLocation(7, 18, 7, 34)))), extension.contexts);
          assertToEqual(new TextLocation(2, 9, 7, 34), extension.sourceInfo.location);
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should parse numeric extension name, parent, and id")
        void parseNumericExtensionNameParentId() {
          // NOT recommended, but possible
          var input = "\n"
                           + "        Extension: 123\n"
                           + "        Parent: 456\n"
                           + "        Id: 789\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.extensions.size());
          var extension = result.extensions.get("123");
          assertEquals("123", extension.name);
          assertEquals("456", extension.parent);
          assertEquals("789", extension.getId());
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should parse an extension with multiple contexts")
        void parseExtensionMultipleContexts() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Parent: ParentExtension\n"
                           + "        Id: some-extension\n"
                           + "        Context: \"some.fhirpath()\", Observation.component, http://example.org/MyPatient#identifier,\n"
                           + "                 \"another.fhirpath(var, 0)\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.extensions.size());
          var extension = result.extensions.get("SomeExtension");
          assertEquals("SomeExtension", extension.name);
          assertEquals("ParentExtension", extension.parent);
          assertEquals("some-extension", extension.getId());
          assertToEqual(List.of(new org.hl7.fhir.sushi.fshtypes.ExtensionContext("some.fhirpath()", true, new SourceInfo("", new TextLocation(5, 18, 5, 35))),
            new org.hl7.fhir.sushi.fshtypes.ExtensionContext("Observation.component", false, new SourceInfo("", new TextLocation(5, 37, 5, 58))),
            new org.hl7.fhir.sushi.fshtypes.ExtensionContext("http://example.org/MyPatient#identifier", false, new SourceInfo("", new TextLocation(5, 60, 5, 99))),
            new org.hl7.fhir.sushi.fshtypes.ExtensionContext("another.fhirpath(var, 0)", true, new SourceInfo("", new TextLocation(6, 18, 6, 43)))), extension.contexts);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should only apply each metadata attribute the first time it is declared")
        void onlyApplyEachMetadataAttributeFirst() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Parent: ParentExtension\n"
                           + "        Id: some-extension\n"
                           + "        Title: \"Some Extension\"\n"
                           + "        Description: \"An extension on something\"\n"
                           + "        Context: SomeElement\n"
                           + "        Parent: DuplicateParentExtension\n"
                           + "        Id: some-duplicate-extension\n"
                           + "        Title: \"Some Duplicate Extension\"\n"
                           + "        Description: \"A duplicated extension on something\"\n"
                           + "        Context: SomeOtherElement\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.extensions.size());
          var extension = result.extensions.get("SomeExtension");
          assertEquals("SomeExtension", extension.name);
          assertEquals("ParentExtension", extension.parent);
          assertEquals("some-extension", extension.getId());
          assertEquals("Some Extension", extension.title);
          assertEquals("An extension on something", extension.description);
          assertToEqual(List.of(new org.hl7.fhir.sushi.fshtypes.ExtensionContext("SomeElement", false, new SourceInfo("", new TextLocation(7, 18, 7, 28)))), extension.contexts);
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should log an error when encountering a duplicate metadata attribute")
        void logErrorEncounteringDuplicateMetadataAttribute() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Parent: ParentExtension\n"
                           + "        Id: some-extension\n"
                           + "        Title: \"Some Extension\"\n"
                           + "        Description: \"An extension on something\"\n"
                           + "        Title: \"Some Duplicate Extension\"\n"
                           + "        Description: \"A duplicated extension on something\"\n"
                           + "        Context: \"SomeContext\"\n"
                           + "        Context: AnotherContext\n"
                           + "        ";
          importSingleText(input, "Dupe.fsh");
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 7\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 8\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 10\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should log an error and skip the extension when encountering an extension with a name used by another extension")
        void logErrorSkipExtensionEncounteringExtension() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Title: \"This Extension\"\n"
                           + "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Title: \"That Extension\"\n"
                           + "        ";
          var result = importSingleText(input, "SameName.fsh");
          assertEquals(1, result.extensions.size());
          var extension = result.extensions.get("SomeExtension");
          assertEquals("This Extension", extension.title);
          assertTrue(matches(Pattern.compile("Extension named SomeExtension already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 5 - 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should log an error and skip the extension when encountering an extension with a name used by another extension in another file")
        void logErrorSkipExtensionEncounteringExtension2() {
          var input1 = "\n"
                            + "          Extension: SomeExtension\n"
                            + "          Title: \"This Extension\"\n"
                            + "        ";
          var input2 = "\n"
                            + "          Extension: SomeExtension\n"
                            + "          Title: \"That Extension\"\n"
                            + "        ";
          var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"),
            new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.extensions.size()).sum());
          var extension = result.get(0).extensions.get("SomeExtension");
          assertEquals("This Extension", extension.title);
          assertTrue(matches(Pattern.compile("Extension named SomeExtension already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Extension #sdMetadata should log an error when the deprecated Mixins keyword is used")
        void logErrorDeprecatedMixinsKeywordUsed() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Mixins: RuleSet1 and RuleSet2\n"
                           + "        ";
          var result = importSingleText(input, "Deprecated.fsh");
          assertEquals(1, result.extensions.size());
          var extension = result.extensions.get("SomeExtension");
          assertEquals("SomeExtension", extension.name);
          assertTrue(matches(Pattern.compile("The 'Mixins' keyword is no longer supported\\.", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Deprecated\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      // TODO: ;
      // 
      //     // Tests for all supported rules are in FSHImporter.SD-Rules.test.ts
      //     // Since Extensions use the same rule parsing ((other) code) StructureDefinitions, only do minimal tests of rules
      @Nested
      @DisplayName("#cardRule")
      class CardRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #cardRule should parse simple card rules")
        void parseSimpleCardRules() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * extension 0..0\n"
                           + "        * value[x] 1..1\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(2, extension.rules.size());
          assertCardRule(extension.rules.get(0), "extension", 0, 0);
          assertCardRule(extension.rules.get(1), "value[x]", 1, 1);
        }

        @Test
        @DisplayName("FSHImporter Extension #cardRule should parse card rules w/ flags")
        void parseCardRulesWFlags() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * extension 0..0\n"
                           + "        * value[x] 1..1 MS N\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(3, extension.rules.size());
          assertCardRule(extension.rules.get(0), "extension", 0, 0);
          assertCardRule(extension.rules.get(1), "value[x]", 1, 1);
          assertFlagRule(
            extension.rules.get(2),
            "value[x]",
            true,
            null,
            null,
            null,
            true,
            null
          );
        }

      }

      @Nested
      @DisplayName("#flagRule")
      class FlagRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #flagRule should parse single-path single-value flag rules")
        void parseSinglePathSingleValueFlag() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * extension MS\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(1, extension.rules.size());
          assertFlagRule(
            extension.rules.get(0),
            "extension",
            true,
            null,
            null,
            null,
            null,
            null
          );
        }

      }

      @Nested
      @DisplayName("#BindingRule")
      class BindingRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #BindingRule should parse value set rules w/ names and strength")
        void parseValueSetRulesWNames() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        Parent: ParentExtension\n"
                           + "        * valueCodeableConcept from ExtensionValueSet (extensible)\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(1, extension.rules.size());
          assertBindingRule(
            extension.rules.get(0),
            "valueCodeableConcept",
            "ExtensionValueSet",
            "extensible"
          );
        }

      }

      @Nested
      @DisplayName("#assignmentRule")
      class AssignmentRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #assignmentRule should parse assigned value boolean rule")
        void parseAssignedValueBooleanRule() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * valueBoolean = true\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(1, extension.rules.size());
          assertAssignmentRule(extension.rules.get(0), "valueBoolean", true);
        }

        @Test
        @DisplayName("FSHImporter Extension #assignmentRule should parse assigned value boolean rule with (exactly) modifier")
        void parseAssignedValueBooleanRuleExactly() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * valueBoolean = true (exactly)\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(1, extension.rules.size());
          assertAssignmentRule(extension.rules.get(0), "valueBoolean", true, true);
        }

      }

      @Nested
      @DisplayName("#onlyRule")
      class OnlyRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #onlyRule should parse an only rule with one type")
        void parseOnlyRuleOneType() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * value[x] only Quantity\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(1, extension.rules.size());
          assertOnlyRule(extension.rules.get(0), "value[x]", ort("Quantity"));
        }

      }

      @Nested
      @DisplayName("#containsRule")
      class ContainsRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #containsRule should parse contains rule with one item")
        void parseContainsRuleOneItem() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * extension contains foo 1..1\n"
                           + "        * extension[foo].value[x] only Quantity\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(3, extension.rules.size());
          assertContainsRule(extension.rules.get(0), "extension", "foo");
          assertCardRule(extension.rules.get(1), "extension[foo]", 1, 1);
          assertOnlyRule(extension.rules.get(2), "extension[foo].value[x]", ort("Quantity"));
        }

        @Test
        @DisplayName("FSHImporter Extension #containsRule should parse contains rule with reserved word (code)")
        void parseContainsRuleReservedWordCode() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * extension contains code 1..1\n"
                           + "        * extension[code].value[x] only Quantity\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(3, extension.rules.size());
          assertContainsRule(extension.rules.get(0), "extension", "code");
          assertCardRule(extension.rules.get(1), "extension[code]", 1, 1);
          assertOnlyRule(extension.rules.get(2), "extension[code].value[x]", ort("Quantity"));
        }

        @Test
        @DisplayName("FSHImporter Extension #containsRule should parse contains rule with item declaring a type")
        void parseContainsRuleItemDeclaringType() {
          var input = "\n"
                           + "        Alias: MaxSizeExtension = http://hl7.org/fhir/StructureDefinition/maxSize\n"
                           + "        Extension: SomeExtension\n"
                           + "        * extension contains MaxSizeExtension named max 1..1\n"
                           + "        * extension[max].value[x] MS N\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(3, extension.rules.size());
          assertContainsRule(extension.rules.get(0), "extension", new ContainsRuleItem("max", "http://hl7.org/fhir/StructureDefinition/maxSize"));
          assertCardRule(extension.rules.get(1), "extension[max]", 1, 1);
          assertFlagRule(
            extension.rules.get(2),
            "extension[max].value[x]",
            true,
            null,
            null,
            null,
            true,
            null
          );
        }

      }

      @Nested
      @DisplayName("#caretValueRule")
      class CaretValueRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #caretValueRule should parse a caret value rule with a path")
        void parseCaretValueRulePath() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * id ^short = \"foo\"\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(1, extension.rules.size());
          assertCaretValueRule(extension.rules.get(0), "id", "short", "foo", false);
        }

      }

      @Nested
      @DisplayName("#obeysRule")
      class ObeysRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #obeysRule should parse an obeys rule with a path and multiple invariants")
        void parseObeysRulePathMultipleInvariants() {
          var input = "\n"
                           + "        Extension: SomeExtension\n"
                           + "        * extension obeys inv-1 and inv-2\n"
                           + "        ";
          var result = importSingleText(input);
          var extension = result.extensions.get("SomeExtension");
          assertEquals(2, extension.rules.size());
          assertObeysRule(extension.rules.get(0), "extension", "inv-1");
          assertObeysRule(extension.rules.get(1), "extension", "inv-2");
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter Extension #insertRule should parse an insert rule with a single RuleSet")
        void parseInsertRuleSingleRuleSet() {
          var input = "\n"
                           + "        Extension: MyExtension\n"
                           + "        * insert MyRuleSet\n"
                           + "        ";
          var result = importSingleText(input, "Insert.fsh");
          var extension = result.extensions.get("MyExtension");
          assertEquals(1, extension.rules.size());
          assertInsertRule(extension.rules.get(0), "", "MyRuleSet");
        }

      }

    }

  }


  static boolean matches(Pattern p, String s) {
    return s != null && p.matcher(s).find();
  }

  static ValueSetComponentFrom from(String system, List<String> valueSets) {
    ValueSetComponentFrom f = new ValueSetComponentFrom();
    f.system = system;
    f.valueSets = valueSets;
    return f;
  }
}
