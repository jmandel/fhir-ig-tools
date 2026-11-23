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

// port of sushi/test/import/FSHImporter.Resource.test.ts (SUSHI 3.21.1)
class FSHImporterResourceTest {
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
    @DisplayName("Resource")
    class ResourceTests {
      @Nested
      @DisplayName("#sdMetadata")
      class SdMetadataTests {
        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should parse the simplest possible resource")
        void parseSimplestPossibleResource() {
          var input = "\n"
                           + "        Resource: Simple\n"
                           + "        ";
          var result = importSingleText(input, "Simple.fsh");
          assertEquals(1, result.resources.size());
          var resource = result.resources.get("Simple");
          assertEquals("Simple", resource.name);
          assertEquals("DomainResource", resource.parent);
          // if no id is explicitly set, should default to name
          assertEquals("Simple", resource.getId());
          assertToEqual(new TextLocation(2, 9, 2, 24), resource.sourceInfo.location);
          assertEquals("Simple.fsh", resource.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should parse resource with name matching various possible tokens recognized as name")
        void parseResourceNameMatchingVariousPossible() {
          // This basically exercises all the tokens we accept for name:
          // SEQUENCE | NUMBER | KW_MS | KW_SU | KW_TU | KW_NORMATIVE | KW_DRAFT | KW_CODES | KW_VSREFERENCE | KW_SYSTEM | KW_UNITS;
          // Since we'll do the same thing over and over (and over), create a function for it
          java.util.function.Consumer<String> testToken = (token) -> {
            var input = leftAlign("\n"
                                       + "          Resource: "
                                       + (token)
                                       + "\n"
                                       + "          * value[x] only boolean\n"
                                       + "          ");
            var result = importSingleText(input);
            assertEquals(0, loggerSpy.getAllLogs("error").size());
            assertNotNull(result);
            assertEquals(1, result.resources.size());
            var resource = result.resources.get(token);
            assertNotNull(resource);
            assertEquals(token, resource.name);
          };
          testToken.accept("MyResource"); // SEQUENCE
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
        @DisplayName("FSHImporter Resource #sdMetadata should log an error when encountering the mixins metadata attribute")
        void logErrorEncounteringMixinsMetadataAttribute() {
          var input = "\n"
                           + "        Resource: TestWithMixins\n"
                           + "        Mixins: Mixin1 and Mixin2 and Mixin3 and Mixin4\n"
                           + "        ";
          importSingleText(input, "TestWithMixins.fsh");
          assertTrue(matches(Pattern.compile("File: TestWithMixins\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should parse resource with additional metadata properties")
        void parseResourceAdditionalMetadataProperties() {
          var input = "\n"
                           + "        Resource: TestWithMeta\n"
                           + "        Id: test-with-meta\n"
                           + "        Title: \"A Test with Meta Resource\"\n"
                           + "        Description: \"Test resource with meta\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.resources.size());
          var resource = result.resources.get("TestWithMeta");
          assertEquals("TestWithMeta", resource.name);
          assertEquals("DomainResource", resource.parent);
          assertEquals("test-with-meta", resource.getId());
          assertEquals("A Test with Meta Resource", resource.title);
          assertEquals("Test resource with meta", resource.description);
          assertToEqual(new TextLocation(2, 9, 5, 46), resource.sourceInfo.location);
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should parse resource with numeric name and id")
        void parseResourceNumericNameId() {
          var input = "\n"
                           + "        Resource: 123\n"
                           + "        Id: 789\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.resources.size());
          var resource = result.resources.get("123");
          assertEquals("123", resource.name);
          assertEquals("789", resource.getId());
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should properly parse a multi-string description")
        void properlyParseMultiStringDescription() {
          var input = "\n"
                           + "        Resource: TestWithMultiStringDesc\n"
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
          assertEquals(1, result.resources.size());
          var resource = result.resources.get("TestWithMultiStringDesc");
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
          assertEquals(String.join("\n", expectedDescriptionLines), resource.description);
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should only apply each metadata attribute the first time it is declared")
        void onlyApplyEachMetadataAttributeFirst() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        Id: test-resource\n"
                           + "        Title: \"A Test Resource\"\n"
                           + "        Description: \"A resource used for tests\"\n"
                           + "        Id: duplicate-test-resource\n"
                           + "        Title: \"Duplicate Test Resource\"\n"
                           + "        Description: \"A duplicated resource\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.resources.size());
          var resource = result.resources.get("TestResource");
          assertEquals("TestResource", resource.name);
          assertEquals("test-resource", resource.getId());
          assertEquals("A Test Resource", resource.title);
          assertEquals("A resource used for tests", resource.description);
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should log an error when encountering a duplicate metadata attribute")
        void logErrorEncounteringDuplicateMetadataAttribute() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        Id: test-resource\n"
                           + "        Title: \"A Test Resource\"\n"
                           + "        Description: \"A resource used for tests\"\n"
                           + "        Id: duplicate-test-resource\n"
                           + "        Title: \"Duplicate Test Resource\"\n"
                           + "        Description: \"A duplicated resource\"\n"
                           + "        ";
          importSingleText(input, "Dupe.fsh");
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 7\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 8\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should log an error and skip the resource when encountering a resource with a name used by another resource")
        void logErrorSkipResourceEncounteringResource() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        Title: \"First Test Resource\"\n"
                           + "\n"
                           + "        Resource: TestResource\n"
                           + "        Title: \"Second Test Resource\"\n"
                           + "        ";
          var result = importSingleText(input, "SameName.fsh");
          assertEquals(1, result.resources.size());
          var resource = result.resources.get("TestResource");
          assertEquals("First Test Resource", resource.title);
          assertTrue(matches(Pattern.compile("Resource named TestResource already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 5 - 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Resource #sdMetadata should log an error and skip the resource when encountering an resource with a name used by another resource in another file")
        void logErrorSkipResourceEncounteringResource2() {
          var input1 = "\n"
                            + "        Resource: SameResource\n"
                            + "        Title: \"First Resource\"\n"
                            + "      ";
          var input2 = "\n"
                            + "        Resource: SameResource\n"
                            + "        Title: \"Second Resource\"\n"
                            + "      ";
          var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"),
            new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.resources.size()).sum());
          var r = result.get(0).resources.get("SameResource");
          assertEquals("First Resource", r.title);
          assertTrue(matches(Pattern.compile("Resource named SameResource already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      // TODO: ;
      // 
      //     // Tests for all supported rules are in FSHImporter.SD-Rules.test.ts
      //     // Since Resources use the same rule parsing ((other) code) StructureDefinitions, only do minimal tests of rules
      @Nested
      @DisplayName("#cardRule")
      class CardRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #cardRule should parse simple card rules")
        void parseSimpleCardRules() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * category 1..5\n"
                           + "        * value[x] 1..1\n"
                           + "        * component 2..*\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(3, resource.rules.size());
          assertCardRule(resource.rules.get(0), "category", 1, 5);
          assertCardRule(resource.rules.get(1), "value[x]", 1, 1);
          assertCardRule(resource.rules.get(2), "component", 2, "*");
        }

        @Test
        @DisplayName("FSHImporter Resource #cardRule should parse card rules w/ multiple flags")
        void parseCardRulesWMultipleFlags() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * category 1..5 MS ?! TU\n"
                           + "        * value[x] 1..1 ?! SU N\n"
                           + "        * component 2..* SU MS D\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(6, resource.rules.size());
          assertCardRule(resource.rules.get(0), "category", 1, 5);
          assertFlagRule(
            resource.rules.get(1),
            "category",
            true,
            null,
            true,
            true,
            null,
            null
          );
          assertCardRule(resource.rules.get(2), "value[x]", 1, 1);
          assertFlagRule(
            resource.rules.get(3),
            "value[x]",
            null,
            true,
            true,
            null,
            true,
            null
          );
          assertCardRule(resource.rules.get(4), "component", 2, "*");
          assertFlagRule(
            resource.rules.get(5),
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
        @DisplayName("FSHImporter Resource #flagRule should parse single-path single-value flag rules")
        void parseSinglePathSingleValueFlag() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * category MS\n"
                           + "        * value[x] ?!\n"
                           + "        * component SU\n"
                           + "        * interpretation TU\n"
                           + "        * note N\n"
                           + "        * bodySite D\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(6, resource.rules.size());
          assertFlagRule(
            resource.rules.get(0),
            "category",
            true,
            null,
            null,
            null,
            null,
            null
          );
          assertFlagRule(
            resource.rules.get(1),
            "value[x]",
            null,
            null,
            true,
            null,
            null,
            null
          );
          assertFlagRule(
            resource.rules.get(2),
            "component",
            null,
            true,
            null,
            null,
            null,
            null
          );
          assertFlagRule(
            resource.rules.get(3),
            "interpretation",
            null,
            null,
            null,
            true,
            null,
            null
          );
          assertFlagRule(
            resource.rules.get(4),
            "note",
            null,
            null,
            null,
            null,
            true,
            null
          );
          assertFlagRule(
            resource.rules.get(5),
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
        @DisplayName("FSHImporter Resource #BindingRule should parse value set rules w/ names and strengths")
        void parseValueSetRulesWNames() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * category from CategoryValueSet (required)\n"
                           + "        * code from CodeValueSet (extensible)\n"
                           + "        * valueCodeableConcept from ValueValueSet (preferred)\n"
                           + "        * component.code from ComponentCodeValueSet (example)\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(4, resource.rules.size());
          assertBindingRule(resource.rules.get(0), "category", "CategoryValueSet", "required");
          assertBindingRule(resource.rules.get(1), "code", "CodeValueSet", "extensible");
          assertBindingRule(resource.rules.get(2), "valueCodeableConcept", "ValueValueSet", "preferred");
          assertBindingRule(resource.rules.get(3), "component.code", "ComponentCodeValueSet", "example");
        }

      }

      @Nested
      @DisplayName("#assignmentRule")
      class AssignmentRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #assignmentRule should parse assigned value boolean rule")
        void parseAssignedValueBooleanRule() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * valueBoolean = true\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(1, resource.rules.size());
          assertAssignmentRule(resource.rules.get(0), "valueBoolean", true);
        }

        @Test
        @DisplayName("FSHImporter Resource #assignmentRule should parse assigned value boolean rule with (exactly) modifier")
        void parseAssignedValueBooleanRuleExactly() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * valueBoolean = true (exactly)\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(1, resource.rules.size());
          assertAssignmentRule(resource.rules.get(0), "valueBoolean", true, true);
        }

      }

      @Nested
      @DisplayName("#onlyRule")
      class OnlyRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #onlyRule should parse an only rule with one type")
        void parseOnlyRuleOneType() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * value[x] only Quantity\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(1, resource.rules.size());
          assertOnlyRule(resource.rules.get(0), "value[x]", ort("Quantity"));
        }

      }

      @Nested
      @DisplayName("#containsRule")
      class ContainsRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #containsRule should parse contains rule with one item")
        void parseContainsRuleOneItem() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * component contains SystolicBP 1..1\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(2, resource.rules.size());
          assertContainsRule(resource.rules.get(0), "component", "SystolicBP");
          assertCardRule(resource.rules.get(1), "component[SystolicBP]", 1, 1);
        }

        @Test
        @DisplayName("FSHImporter Resource #containsRule should parse contains rule with one item declaring an aliased type")
        void parseContainsRuleOneItemDeclaring() {
          var input = "\n"
                           + "        Alias: OffsetExtension = http://hl7.org/fhir/StructureDefinition/observation-timeOffset\n"
                           + "        Resource: TestResource\n"
                           + "        * component.extension contains OffsetExtension named offset 0..1\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(2, resource.rules.size());
          assertContainsRule(resource.rules.get(0), "component.extension", new ContainsRuleItem("offset", "http://hl7.org/fhir/StructureDefinition/observation-timeOffset"));
          assertCardRule(resource.rules.get(1), "component.extension[offset]", 0, 1);
        }

      }

      @Nested
      @DisplayName("#caretValueRule")
      class CaretValueRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #caretValueRule should parse caret value rules with a path")
        void parseCaretValueRulesPath() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * status ^short = \"foo\"\n"
                           + "        * status ^sliceIsConstraining = false\n"
                           + "        * status ^code[0] = foo#bar \"baz\"\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertCaretValueRule(resource.rules.get(0), "status", "short", "foo", false);
          assertCaretValueRule(resource.rules.get(1), "status", "sliceIsConstraining", false, false);
          assertCaretValueRule(
            resource.rules.get(2),
            "status",
            "code[0]",
            new FshCode("bar", "foo", "baz").withLocation(new int[] {5, 29, 5, 41}).withFile(""),
            false
          );
        }

        @Test
        @DisplayName("FSHImporter Resource #caretValueRule should parse caret value rules with triple-quoted string")
        void parseCaretValueRulesTripleQuoted() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * status ^short = \"foo\"\n"
                           + "        * status ^definition = \"\"\"\n"
                           + "          This definition includes markdown for unordered lists:\n"
                           + "          * Level 1 list item 1\n"
                           + "            * Level 2 list item 1a\n"
                           + "            * Level 2 list item 1b\n"
                           + "          * Level 1 list item 2\n"
                           + "        \"\"\"\n"
                           + "        * status ^sliceIsConstraining = false\n"
                           + "        * status ^code[0] = foo#bar \"baz\"\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertCaretValueRule(resource.rules.get(0), "status", "short", "foo", false);
          var expectedDefinition =
            "This definition includes markdown for unordered lists:\n* Level 1 list item 1\n  * Level 2 list item 1a\n  * Level 2 list item 1b\n* Level 1 list item 2";
          assertCaretValueRule(resource.rules.get(1), "status", "definition", expectedDefinition, false);
          assertCaretValueRule(resource.rules.get(2), "status", "sliceIsConstraining", false, false);
          assertCaretValueRule(
            resource.rules.get(3),
            "status",
            "code[0]",
            new FshCode("bar", "foo", "baz").withLocation(new int[] {12, 29, 12, 41}).withFile(""),
            false
          );
        }

      }

      @Nested
      @DisplayName("#obeysRule")
      class ObeysRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #obeysRule should parse an obeys rule with multiple invariants and a path")
        void parseObeysRuleMultipleInvariantsPath() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * category obeys SomeInvariant and ThisInvariant and ThatInvariant\n"
                           + "        ";
          var result = importSingleText(input, "Obeys.fsh");
          var resource = result.resources.get("TestResource");
          assertEquals(3, resource.rules.size());
          assertObeysRule(resource.rules.get(0), "category", "SomeInvariant");
          assertObeysRule(resource.rules.get(1), "category", "ThisInvariant");
          assertObeysRule(resource.rules.get(2), "category", "ThatInvariant");
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #insertRule should parse an insert rule with a single RuleSet")
        void parseInsertRuleSingleRuleSet() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * insert MyRuleSet\n"
                           + "        ";
          var result = importSingleText(input, "Insert.fsh");
          var resource = result.resources.get("TestResource");
          assertEquals(1, resource.rules.size());
          assertInsertRule(resource.rules.get(0), "", "MyRuleSet");
        }

      }

      @Nested
      @DisplayName("#addElementRule")
      class AddElementRuleTests {
        @Test
        @DisplayName("FSHImporter Resource #addElementRule should parse complete add element rules")
        void parseCompleteAddElementRules() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * isValid 1..1 MS boolean \"is it valid?\"\n"
                           + "        * stuff 0..* string \"just stuff\" \"a list of some stuff\"\n"
                           + "        * address 1..* Address \"Just an address\"\n"
                           + "        * extraThing 0..3 contentReference http://example.org/StructureDefinition/Thing#Thing.extra \"extra thing\"\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(4, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid", new AddElementArgs(1, "1", new Flags(true, null, null, null, null, null), List.of(ort("boolean")), new Defs("is it valid?", "is it valid?", null)));
          assertAddElementRule(resource.rules.get(1), "stuff", new AddElementArgs(0, "*", null, List.of(ort("string")), new Defs("just stuff", "a list of some stuff", null)));
          assertAddElementRule(resource.rules.get(2), "address", new AddElementArgs(1, "*", null, List.of(ort("Address")), new Defs("Just an address", "Just an address", null)));
          assertAddElementRule(resource.rules.get(3), "extraThing", new AddElementArgs(0, "3", null, List.of(), new Defs("extra thing", "extra thing", "http://example.org/StructureDefinition/Thing#Thing.extra")));
        }

        @Test
        @DisplayName("FSHImporter Resource #addElementRule should parse complete add element rules with triple-quoted string for definition")
        void parseCompleteAddElementRulesTriple() {
          var input = "\n"
                           + "        Resource: TestResource\n"
                           + "        * isValid 1..1 MS boolean \"is it valid?\"\n"
                           + "        * stuff 0..* string \"just stuff\" \"a list of some stuff\"\n"
                           + "        * address 1..* Address \"Just an address\" \n"
                           + "          \"\"\"\n"
                           + "            This definition for address includes markdown for unordered lists:\n"
                           + "            * Level 1 list item 1\n"
                           + "              * Level 2 list item 1a\n"
                           + "              * Level 2 list item 1b\n"
                           + "            * Level 1 list item 2\n"
                           + "          \"\"\"\n"
                           + "        ";
          var result = importSingleText(input);
          var resource = result.resources.get("TestResource");
          assertEquals(3, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid", new AddElementArgs(1, "1", new Flags(true, null, null, null, null, null), List.of(ort("boolean")), new Defs("is it valid?", "is it valid?", null)));
          assertAddElementRule(resource.rules.get(1), "stuff", new AddElementArgs(0, "*", null, List.of(ort("string")), new Defs("just stuff", "a list of some stuff", null)));
          var expectedDefinition =
            "This definition for address includes markdown for unordered lists:\n* Level 1 list item 1\n  * Level 2 list item 1a\n  * Level 2 list item 1b\n* Level 1 list item 2";
          assertAddElementRule(resource.rules.get(2), "address", new AddElementArgs(1, "*", null, List.of(ort("Address")), new Defs("Just an address", expectedDefinition, null)));
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
