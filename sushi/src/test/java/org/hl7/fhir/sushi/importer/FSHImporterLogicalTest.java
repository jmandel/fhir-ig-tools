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

// port of sushi/test/import/FSHImporter.Logical.test.ts (SUSHI 3.21.1)
class FSHImporterLogicalTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void beforeeach() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("Logical")
    class LogicalTests {
      @Nested
      @DisplayName("#sdMetadata")
      class SdMetadataTests {
        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should parse the simplest possible logical model")
        void parseSimplestPossibleLogicalModel() {
          var input = "\n"
                           + "        Logical: MyLogicalModel\n"
                           + "        ";
          var result = importSingleText(input, "Simple.fsh");
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("MyLogicalModel");
          assertEquals("MyLogicalModel", logical.name);
          assertEquals("Base", logical.parent);
          // if no id is explicitly set, should default to name
          assertEquals("MyLogicalModel", logical.getId());
          assertToEqual(new TextLocation(2, 9, 2, 31), logical.sourceInfo.location);
          assertEquals("Simple.fsh", logical.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should parse logical model with name matching various possible tokens recognized as name")
        void parseLogicalModelNameMatchingVarious() {
          // This basically exercises all the tokens we accept for name:
          // SEQUENCE | NUMBER | KW_MS | KW_SU | KW_TU | KW_NORMATIVE | KW_DRAFT | KW_CODES | KW_VSREFERENCE | KW_SYSTEM | KW_UNITS;
          // Since we'll do the same thing over and over (and over), create a function for it
          java.util.function.Consumer<String> testToken = (token) -> {
            var input = leftAlign("\n"
                                       + "          Logical: "
                                       + (token)
                                       + "\n"
                                       + "          Parent: BaseObservationModel\n"
                                       + "          * value[x] only boolean\n"
                                       + "          ");
            var result = importSingleText(input);
            assertEquals(0, loggerSpy.getAllLogs("error").size());
            assertNotNull(result);
            assertEquals(1, result.logicals.size());
            var logical = result.logicals.get(token);
            assertNotNull(logical);
            assertEquals(token, logical.name);
          };
          testToken.accept("MyLogical"); // SEQUENCE
          testToken.accept("123"); // NUMBER
          testToken.accept("MS"); // KW_MS
          testToken.accept("SU"); // KW_SU
          testToken.accept("TU"); // KW_TU
          testToken.accept("N"); // KW_NORMATIVE
          testToken.accept("D"); // KW_DRAFT
          testToken.accept("codes"); // KW_CODES
          testToken.accept("valueset"); // KW_VSREFERENCE
          testToken.accept("system"); // KW_SYSTEM
          testToken.accept("units"); // KW_UNITS
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should log an error when encountering the mixins metadata attribute")
        void logErrorEncounteringMixinsMetadataAttribute() {
          var input = "\n"
                           + "        Logical: TestWithMixins\n"
                           + "        Mixins: Mixin1 and Mixin2 and Mixin3 and Mixin4\n"
                           + "        ";
          importSingleText(input, "TestWithMixins.fsh");
          assertTrue(matches(Pattern.compile("File: TestWithMixins\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should parse logical model with additional metadata properties")
        void parseLogicalModelAdditionalMetadataProperties() {
          var input = "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: BaseObservationModel\n"
                           + "        Id: observation-model\n"
                           + "        Title: \"An Observation-based Logical Model\"\n"
                           + "        Description: \"A logical model based on Observation\"\n"
                           + "        Characteristics: #can-be-target\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("MyObservationModel");
          assertEquals("MyObservationModel", logical.name);
          assertEquals("BaseObservationModel", logical.parent);
          assertEquals("observation-model", logical.getId());
          assertEquals("An Observation-based Logical Model", logical.title);
          assertEquals("A logical model based on Observation", logical.description);
          assertToEqual(List.of("can-be-target"), logical.characteristics);
          assertToEqual(new TextLocation(2, 9, 7, 39), logical.sourceInfo.location);
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should parse logical model with numeric name, parent, and id")
        void parseLogicalModelNumericNameParent() {
          var input = "\n"
                           + "        Logical: 123\n"
                           + "        Parent: 456\n"
                           + "        Id: 789\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("123");
          assertEquals("123", logical.name);
          assertEquals("456", logical.parent);
          assertEquals("789", logical.getId());
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should properly parse a multi-string description")
        void properlyParseMultiStringDescription() {
          var input = "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: BaseObservationModel\n"
                           + "        Description:\n"
                           + "          \"\"\"\n"
                           + "          This is a multi-string description\n"
                           + "          with a couple of paragraphs.\n"
                           + "\n"
                           + "          This is the second paragraph.  It has bullet points w/ indentation:\n"
                           + "\n"
                           + "          * Bullet 1\n"
                           + "            * Bullet A\n"
                           + "            * Bullet B\n"
                           + "              * Bullet i\n"
                           + "            * Bullet C\n"
                           + "          * Bullet 2\n"
                           + "          \"\"\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("MyObservationModel");
          var expectedDescriptionLines = List.of("This is a multi-string description",
            "with a couple of paragraphs.",
            "",
            "This is the second paragraph.  It has bullet points w/ indentation:",
            "",
            "* Bullet 1",
            "  * Bullet A",
            "  * Bullet B",
            "    * Bullet i",
            "  * Bullet C",
            "* Bullet 2");
          assertEquals(String.join("\n", expectedDescriptionLines), logical.description);
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should accept and translate an alias for the parent")
        void acceptTranslateAliasForParent() {
          var input = "\n"
                           + "        Alias: OBS = http://example.com/fhir/StructureDefinition/BaseObservationModel\n"
                           + "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: OBS\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("MyObservationModel");
          assertEquals("MyObservationModel", logical.name);
          assertEquals("http://example.com/fhir/StructureDefinition/BaseObservationModel", logical.parent);
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should parse a list of multiple characteristics")
        void parseListMultipleCharacteristics() {
          var input = "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: BaseObservationModel\n"
                           + "        Id: observation-model\n"
                           + "        Title: \"An Observation-based Logical Model\"\n"
                           + "        Description: \"A logical model based on Observation\"\n"
                           + "        Characteristics: #has-range, #is-continuous , #can-bind,\n"
                           + "                         #has-units\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("MyObservationModel");
          assertEquals("MyObservationModel", logical.name);
          assertEquals("BaseObservationModel", logical.parent);
          assertEquals("observation-model", logical.getId());
          assertEquals("An Observation-based Logical Model", logical.title);
          assertEquals("A logical model based on Observation", logical.description);
          assertToEqual(List.of("has-range",
            "is-continuous",
            "can-bind",
            "has-units"), logical.characteristics);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should only apply each metadata attribute the first time it is declared")
        void onlyApplyEachMetadataAttributeFirst() {
          var input = "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: BaseObservationModel\n"
                           + "        Id: observation-model\n"
                           + "        Title: \"An Observation-based Logical Model\"\n"
                           + "        Description: \"A logical model based on Observation\"\n"
                           + "        Parent: DuplicateBaseObservationModel\n"
                           + "        Id: duplicate-observation-model\n"
                           + "        Title: \"Duplicate Observation-based Logical Model\"\n"
                           + "        Description: \"Duplicate logical model based on Observation\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("MyObservationModel");
          assertEquals("MyObservationModel", logical.name);
          assertEquals("BaseObservationModel", logical.parent);
          assertEquals("observation-model", logical.getId());
          assertEquals("An Observation-based Logical Model", logical.title);
          assertEquals("A logical model based on Observation", logical.description);
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should log an error when encountering a duplicate metadata attribute")
        void logErrorEncounteringDuplicateMetadataAttribute() {
          var input = "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: BaseObservationModel\n"
                           + "        Id: observation-model\n"
                           + "        Title: \"An Observation-based Logical Model\"\n"
                           + "        Description: \"A logical model based on Observation\"\n"
                           + "        Parent: DuplicateBaseObservationModel\n"
                           + "        Id: duplicate-observation-model\n"
                           + "        Title: \"Duplicate Observation-based Logical Model\"\n"
                           + "        Description: \"Duplicate logical model based on Observation\"\n"
                           + "        ";
          importSingleText(input, "Dupe.fsh");
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 7\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-4, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 8\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 9\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 10\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should log an error and skip the logical model when encountering a logical model with a name used by another logical model")
        void logErrorSkipLogicalModelEncountering() {
          var input = "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: BaseObservationModel\n"
                           + "        Title: \"An Observation-based Logical Model\"\n"
                           + "\n"
                           + "        Logical: MyObservationModel\n"
                           + "        Parent: BaseObservationModel\n"
                           + "        Title: \"Second Observation-based Logical Model\"\n"
                           + "        ";
          var result = importSingleText(input, "SameName.fsh");
          assertEquals(1, result.logicals.size());
          var logical = result.logicals.get("MyObservationModel");
          assertEquals("An Observation-based Logical Model", logical.title);
          assertTrue(matches(Pattern.compile("Logical Model named MyObservationModel already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 6 - 8\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Logical #sdMetadata should log an error and skip the logical when encountering an logical with a name used by another logical in another file")
        void logErrorSkipLogicalEncounteringLogical() {
          var input1 = "\n"
                            + "          Logical: MyObservationModel\n"
                            + "          Parent: BaseObservationModel\n"
                            + "          Title: \"An Observation-based Logical Model\"\n"
                            + "        ";
          var input2 = "\n"
                            + "          Logical: MyObservationModel\n"
                            + "          Parent: BaseObservationModel\n"
                            + "          Title: \"Second Observation-based Logical Model\"\n"
                            + "        ";
          var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"),
            new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.logicals.size()).sum());
          var l = result.get(0).logicals.get("MyObservationModel");
          assertEquals("An Observation-based Logical Model", l.title);
          assertTrue(matches(Pattern.compile("Logical Model named MyObservationModel already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      // TODO: ;
      // 
      //     // Tests for all supported rules are in FSHImporter.SDRules.test.ts
      //     // Since Logicals use the same rule parsing ((other) code) StructureDefinitions, only do minimal tests of rules
      @Nested
      @DisplayName("#cardRule")
      class CardRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #cardRule should parse simple card rules")
        void parseSimpleCardRules() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * category 1..5\n"
                           + "        * value[x] 1..1\n"
                           + "        * component 2..*\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(3, logical.rules.size());
          assertCardRule(logical.rules.get(0), "category", 1, 5);
          assertCardRule(logical.rules.get(1), "value[x]", 1, 1);
          assertCardRule(logical.rules.get(2), "component", 2, "*");
        }

        @Test
        @DisplayName("FSHImporter Logical #cardRule should parse card rules w/ multiple flags")
        void parseCardRulesWMultipleFlags() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * category 1..5 MS ?! TU\n"
                           + "        * value[x] 1..1 ?! SU N\n"
                           + "        * component 2..* SU MS D\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(6, logical.rules.size());
          assertCardRule(logical.rules.get(0), "category", 1, 5);
          assertFlagRule(
            logical.rules.get(1),
            "category",
            true,
            null,
            true,
            true,
            null,
            null
          );
          assertCardRule(logical.rules.get(2), "value[x]", 1, 1);
          assertFlagRule(
            logical.rules.get(3),
            "value[x]",
            null,
            true,
            true,
            null,
            true,
            null
          );
          assertCardRule(logical.rules.get(4), "component", 2, "*");
          assertFlagRule(
            logical.rules.get(5),
            "component",
            true,
            true,
            null,
            null,
            null,
            true
          );
        }

      }

      @Nested
      @DisplayName("#flagRule")
      class FlagRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #flagRule should parse single-path single-value flag rules")
        void parseSinglePathSingleValueFlag() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * category MS\n"
                           + "        * value[x] ?!\n"
                           + "        * component SU\n"
                           + "        * interpretation TU\n"
                           + "        * note N\n"
                           + "        * bodySite D\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(6, logical.rules.size());
          assertFlagRule(
            logical.rules.get(0),
            "category",
            true,
            null,
            null,
            null,
            null,
            null
          );
          assertFlagRule(
            logical.rules.get(1),
            "value[x]",
            null,
            null,
            true,
            null,
            null,
            null
          );
          assertFlagRule(
            logical.rules.get(2),
            "component",
            null,
            true,
            null,
            null,
            null,
            null
          );
          assertFlagRule(
            logical.rules.get(3),
            "interpretation",
            null,
            null,
            null,
            true,
            null,
            null
          );
          assertFlagRule(
            logical.rules.get(4),
            "note",
            null,
            null,
            null,
            null,
            true,
            null
          );
          assertFlagRule(
            logical.rules.get(5),
            "bodySite",
            null,
            null,
            null,
            null,
            null,
            true
          );
        }

      }

      @Nested
      @DisplayName("#BindingRule")
      class BindingRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #BindingRule should parse value set rules w/ names and strengths")
        void parseValueSetRulesWNames() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * category from CategoryValueSet (required)\n"
                           + "        * code from CodeValueSet (extensible)\n"
                           + "        * valueCodeableConcept from ValueValueSet (preferred)\n"
                           + "        * component.code from ComponentCodeValueSet (example)\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(4, logical.rules.size());
          assertBindingRule(logical.rules.get(0), "category", "CategoryValueSet", "required");
          assertBindingRule(logical.rules.get(1), "code", "CodeValueSet", "extensible");
          assertBindingRule(logical.rules.get(2), "valueCodeableConcept", "ValueValueSet", "preferred");
          assertBindingRule(logical.rules.get(3), "component.code", "ComponentCodeValueSet", "example");
        }

      }

      @Nested
      @DisplayName("#assignmentRule")
      class AssignmentRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #assignmentRule should parse assigned value boolean rule")
        void parseAssignedValueBooleanRule() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * valueBoolean = true\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(1, logical.rules.size());
          assertAssignmentRule(logical.rules.get(0), "valueBoolean", true);
        }

        @Test
        @DisplayName("FSHImporter Logical #assignmentRule should parse assigned value boolean rule with (exactly) modifier")
        void parseAssignedValueBooleanRuleExactly() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * valueBoolean = true (exactly)\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(1, logical.rules.size());
          assertAssignmentRule(logical.rules.get(0), "valueBoolean", true, true);
        }

      }

      @Nested
      @DisplayName("#onlyRule")
      class OnlyRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #onlyRule should parse an only rule with one type")
        void parseOnlyRuleOneType() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * value[x] only Quantity\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(1, logical.rules.size());
          assertOnlyRule(logical.rules.get(0), "value[x]", ort("Quantity"));
        }

      }

      @Nested
      @DisplayName("#containsRule")
      class ContainsRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #containsRule should parse contains rule with one item")
        void parseContainsRuleOneItem() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * component contains SystolicBP 1..1\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(2, logical.rules.size());
          assertContainsRule(logical.rules.get(0), "component", "SystolicBP");
          assertCardRule(logical.rules.get(1), "component[SystolicBP]", 1, 1);
        }

        @Test
        @DisplayName("FSHImporter Logical #containsRule should parse contains rule with one item declaring an aliased type")
        void parseContainsRuleOneItemDeclaring() {
          var input = "\n"
                           + "        Alias: OffsetExtension = http://hl7.org/fhir/StructureDefinition/observation-timeOffset\n"
                           + "        Logical: LogicalModel\n"
                           + "        * component.extension contains OffsetExtension named offset 0..1\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(2, logical.rules.size());
          assertContainsRule(logical.rules.get(0), "component.extension", new ContainsRuleItem("offset", "http://hl7.org/fhir/StructureDefinition/observation-timeOffset"));
          assertCardRule(logical.rules.get(1), "component.extension[offset]", 0, 1);
        }

      }

      @Nested
      @DisplayName("#caretValueRule")
      class CaretValueRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #caretValueRule should parse caret value rules with a path")
        void parseCaretValueRulesPath() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * status ^short = \"foo\"\n"
                           + "        * status ^sliceIsConstraining = false\n"
                           + "        * status ^code[0] = foo#bar \"baz\"\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertCaretValueRule(logical.rules.get(0), "status", "short", "foo", false);
          assertCaretValueRule(logical.rules.get(1), "status", "sliceIsConstraining", false, false);
          assertCaretValueRule(
            logical.rules.get(2),
            "status",
            "code[0]",
            new FshCode("bar", "foo", "baz").withLocation(new int[] {5, 29, 5, 41}).withFile(""),
            false
          );
        }

      }

      @Nested
      @DisplayName("#obeysRule")
      class ObeysRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #obeysRule should parse an obeys rule with multiple invariants and a path")
        void parseObeysRuleMultipleInvariantsPath() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * category obeys SomeInvariant and ThisInvariant and ThatInvariant\n"
                           + "        ";
          var result = importSingleText(input, "Obeys.fsh");
          var logical = result.logicals.get("LogicalModel");
          assertEquals(3, logical.rules.size());
          assertObeysRule(logical.rules.get(0), "category", "SomeInvariant");
          assertObeysRule(logical.rules.get(1), "category", "ThisInvariant");
          assertObeysRule(logical.rules.get(2), "category", "ThatInvariant");
        }

        @Test
        @DisplayName("FSHImporter Logical #obeysRule should parse an obeys rule on an element named obeys")
        void parseObeysRuleElementNamedObeys() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * obeys obeys SomeInvariant and ThisInvariant and ThatInvariant\n"
                           + "        ";
          var result = importSingleText(input, "Obeys.fsh");
          var logical = result.logicals.get("LogicalModel");
          assertEquals(3, logical.rules.size());
          assertObeysRule(logical.rules.get(0), "obeys", "SomeInvariant");
          assertObeysRule(logical.rules.get(1), "obeys", "ThisInvariant");
          assertObeysRule(logical.rules.get(2), "obeys", "ThatInvariant");
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #insertRule should parse an insert rule with a single RuleSet")
        void parseInsertRuleSingleRuleSet() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * insert MyRuleSet\n"
                           + "        ";
          var result = importSingleText(input, "Insert.fsh");
          var logical = result.logicals.get("LogicalModel");
          assertEquals(1, logical.rules.size());
          assertInsertRule(logical.rules.get(0), "", "MyRuleSet");
        }

      }

      @Nested
      @DisplayName("#addElementRule")
      class AddElementRuleTests {
        @Test
        @DisplayName("FSHImporter Logical #addElementRule should parse complete add element rules")
        void parseCompleteAddElementRules() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * isValid 1..1 MS boolean \"is it valid?\"\n"
                           + "        * stuff 0..* string \"just stuff\" \"a list of some stuff\"\n"
                           + "        * address 1..* Address\n"
                           + "        ";
          var result = importSingleText(input);
          var logical = result.logicals.get("LogicalModel");
          assertEquals(3, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "isValid", new AddElementArgs(1, "1", new Flags(true, null, null, null, null, null), List.of(ort("boolean")), new Defs("is it valid?", null, null)));
          assertAddElementRule(logical.rules.get(1), "stuff", new AddElementArgs(0, "*", null, List.of(ort("string")), new Defs("just stuff", "a list of some stuff", null)));
          assertAddElementRule(logical.rules.get(2), "address", new AddElementArgs(1, "*", null, List.of(ort("Address")), null));
        }

        @Test
        @DisplayName("FSHImporter Logical #addElementRule should parse add element rules where the element name is numeric")
        void parseAddElementRulesWhereElement() {
          var input = leftAlign("\n"
                                     + "        Logical: LogicalModel\n"
                                     + "        * 7 0..1 string \"This element's name is seven\"\n"
                                     + "        * 35 0..* BackboneElement \"This complex element is three five\"\n"
                                     + "        * 35.79 1..1 boolean \"This is seven nine, contained within three five\"\n"
                                     + "        ");
          var result = importSingleText(input, "NumericElementNames.fsh");
          var logical = result.logicals.get("LogicalModel");
          assertEquals(3, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "7", new AddElementArgs(0, "1", null, List.of(ort("string")), new Defs("This element's name is seven", null, null)));
          assertAddElementRule(logical.rules.get(1), "35", new AddElementArgs(0, "*", null, List.of(ort("BackboneElement")), new Defs("This complex element is three five", null, null)));
          assertAddElementRule(logical.rules.get(2), "35.79", new AddElementArgs(1, "1", null, List.of(ort("boolean")), new Defs("This is seven nine, contained within three five", null, null)));
          assertEquals(0, loggerSpy.getAllLogs("error").size());
        }

        @Test
        @DisplayName("FSHImporter Logical #addElementRule should parse add element rules where the element name is a FSH keyword")
        void parseAddElementRulesWhereElement2() {
          var input = leftAlign("\n"
                                     + "        Logical: KeywordLogical\n"
                                     + "        * MS 0..1 string \"Element named MS\"\n"
                                     + "        * SU 0..2 number \"Element named SU\"\n"
                                     + "        * TU 1..1 boolean \"Element named TU\"\n"
                                     + "        * N 0..* code \"Element named N\"\n"
                                     + "        * D 1..* MS Coding \"Element named D\"\n"
                                     + "        * from 0..1 Address \"From an address\"\n"
                                     + "        * contains 0..* string \"Contains many strings\"\n"
                                     + "        * named 1..1 HumanName \"Has a name\"\n"
                                     + "        * and 0..1 boolean \"And what?\"\n"
                                     + "        * only 0..1 number \"Only number\"\n"
                                     + "        * or 0..3 string \"Or three strings\"\n"
                                     + "        * obeys 1..1 boolean \"Obey boolean\"\n"
                                     + "        * true 0..1 boolean \"Please don't make an element named true\"\n"
                                     + "        * false 0..1 boolean \"Please don't make an element named false, either\"\n"
                                     + "        * include 0..1 Coding \"Include a coding\"\n"
                                     + "        * exclude 0..1 Coding \"Exclude a coding\"\n"
                                     + "        * codes 0..* code \"List of codes\"\n"
                                     + "        * where 1..1 Address \"Where is it?\"\n"
                                     + "        * valueset 0..1 uri \"Give me a value set\"\n"
                                     + "        * system 0..1 uri \"Give me a system\"\n"
                                     + "        * contentReference 1..1 contentReference http://example.org/StructureDefinition/Keywords#Keywords.content \"A content reference with a content reference\"\n"
                                     + "        ");
          var result = importSingleText(input, "KeywordLogical.fsh");
          var logical = result.logicals.get("KeywordLogical");
          assertEquals(21, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "MS", new AddElementArgs(0, "1", null, List.of(ort("string")), new Defs("Element named MS", null, null)));
          assertAddElementRule(logical.rules.get(1), "SU", new AddElementArgs(0, "2", null, List.of(ort("number")), new Defs("Element named SU", null, null)));
          assertAddElementRule(logical.rules.get(2), "TU", new AddElementArgs(1, "1", null, List.of(ort("boolean")), new Defs("Element named TU", null, null)));
          assertAddElementRule(logical.rules.get(3), "N", new AddElementArgs(0, "*", null, List.of(ort("code")), new Defs("Element named N", null, null)));
          assertAddElementRule(logical.rules.get(4), "D", new AddElementArgs(1, "*", new Flags(true, null, null, null, null, null), List.of(ort("Coding")), new Defs("Element named D", null, null)));
          assertAddElementRule(logical.rules.get(5), "from", new AddElementArgs(0, "1", null, List.of(ort("Address")), new Defs("From an address", null, null)));
          assertAddElementRule(logical.rules.get(6), "contains", new AddElementArgs(0, "*", null, List.of(ort("string")), new Defs("Contains many strings", null, null)));
          assertAddElementRule(logical.rules.get(7), "named", new AddElementArgs(1, "1", null, List.of(ort("HumanName")), new Defs("Has a name", null, null)));
          assertAddElementRule(logical.rules.get(8), "and", new AddElementArgs(0, "1", null, List.of(ort("boolean")), new Defs("And what?", null, null)));
          assertAddElementRule(logical.rules.get(9), "only", new AddElementArgs(0, "1", null, List.of(ort("number")), new Defs("Only number", null, null)));
          assertAddElementRule(logical.rules.get(10), "or", new AddElementArgs(0, "3", null, List.of(ort("string")), new Defs("Or three strings", null, null)));
          assertAddElementRule(logical.rules.get(11), "obeys", new AddElementArgs(1, "1", null, List.of(ort("boolean")), new Defs("Obey boolean", null, null)));
          assertAddElementRule(logical.rules.get(12), "true", new AddElementArgs(0, "1", null, List.of(ort("boolean")), new Defs("Please don't make an element named true", null, null)));
          assertAddElementRule(logical.rules.get(13), "false", new AddElementArgs(0, "1", null, List.of(ort("boolean")), new Defs("Please don't make an element named false, either", null, null)));
          assertAddElementRule(logical.rules.get(14), "include", new AddElementArgs(0, "1", null, List.of(ort("Coding")), new Defs("Include a coding", null, null)));
          assertAddElementRule(logical.rules.get(15), "exclude", new AddElementArgs(0, "1", null, List.of(ort("Coding")), new Defs("Exclude a coding", null, null)));
          assertAddElementRule(logical.rules.get(16), "codes", new AddElementArgs(0, "*", null, List.of(ort("code")), new Defs("List of codes", null, null)));
          assertAddElementRule(logical.rules.get(17), "where", new AddElementArgs(1, "1", null, List.of(ort("Address")), new Defs("Where is it?", null, null)));
          assertAddElementRule(logical.rules.get(18), "valueset", new AddElementArgs(0, "1", null, List.of(ort("uri")), new Defs("Give me a value set", null, null)));
          assertAddElementRule(logical.rules.get(19), "system", new AddElementArgs(0, "1", null, List.of(ort("uri")), new Defs("Give me a system", null, null)));
          assertAddElementRule(logical.rules.get(20), "contentReference", new AddElementArgs(1, "1", null, List.of(), new Defs("A content reference with a content reference", null, "http://example.org/StructureDefinition/Keywords#Keywords.content")));
        }

        @Test
        @DisplayName("FSHImporter Logical #addElementRule should parse content reference add element rules")
        void parseContentReferenceAddElementRules() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * oranges 1..* MS contentReference http://example.org/StructureDefinition/Citrus#Citrus.orange \"oranges\" \"oranges are a citrus\"\n"
                           + "        * apples 0..3 contentReference http://example.org/StructureDefinition/Fruit#Fruit.apple \"apples\"\n"
                           + "        ";
          var result = importSingleText(input, "ContentReference.fsh");
          var logical = result.logicals.get("LogicalModel");
          assertEquals(2, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "oranges", new AddElementArgs(1, "*", new Flags(true, null, null, null, null, null), List.of(), new Defs("oranges", "oranges are a citrus", "http://example.org/StructureDefinition/Citrus#Citrus.orange")));
          assertAddElementRule(logical.rules.get(1), "apples", new AddElementArgs(0, "3", null, List.of(), new Defs("apples", "apples", "http://example.org/StructureDefinition/Fruit#Fruit.apple")));
        }

        @Test
        @DisplayName("FSHImporter Logical #addElementRule should parse content reference add element rules that use an alias")
        void parseContentReferenceAddElementRules2() {
          var input = "\n"
                           + "        Alias: $orange = http://example.org/StructureDefinition/Citrus#Citrus.orange\n"
                           + "        Logical: LogicalModel\n"
                           + "        * oranges 1..* MS contentReference $orange \"oranges\" \"oranges are a citrus\"\n"
                           + "        * apples 0..3 contentReference http://example.org/StructureDefinition/Fruit#Fruit.apple \"apples\"\n"
                           + "        ";
          var result = importSingleText(input, "ContentReference.fsh");
          var logical = result.logicals.get("LogicalModel");
          assertEquals(2, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "oranges", new AddElementArgs(1, "*", new Flags(true, null, null, null, null, null), List.of(), new Defs("oranges", "oranges are a citrus", "http://example.org/StructureDefinition/Citrus#Citrus.orange")));
          assertAddElementRule(logical.rules.get(1), "apples", new AddElementArgs(0, "3", null, List.of(), new Defs("apples", "apples", "http://example.org/StructureDefinition/Fruit#Fruit.apple")));
        }

        @Test
        @DisplayName("FSHImporter Logical #addElementRule should log a warning when a data type is defined with a flag value")
        void logWarningDataTypeDefinedFlag() {
          var input = "\n"
                           + "        Logical: LogicalModel\n"
                           + "        * isValid 1..1 MS boolean \"is it valid?\"\n"
                           + "        * stuff 0..* N \"just stuff\" \"a list of some stuff\"\n"
                           + "        * address 1..* Address\n"
                           + "        ";
          var result = importSingleText(input, "WarnDataType.fsh");
          var logical = result.logicals.get("LogicalModel");
          assertEquals(3, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "isValid", new AddElementArgs(1, "1", new Flags(true, null, null, null, null, null), List.of(ort("boolean")), new Defs("is it valid?", null, null)));
          assertAddElementRule(logical.rules.get(1), "stuff", new AddElementArgs(0, "*", null, List.of(ort("N")), new Defs("just stuff", "a list of some stuff", null)));
          assertAddElementRule(logical.rules.get(2), "address", new AddElementArgs(1, "*", null, List.of(ort("Address")), null));
          assertTrue(matches(Pattern.compile("The targetType 'N' appears to be a flag value rather than a valid target data type.", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
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
