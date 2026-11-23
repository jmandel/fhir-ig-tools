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

// port of sushi/test/import/FSHImporter.CodeSystem.test.ts (SUSHI 3.21.1)
class FSHImporterCodeSystemTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @Nested
    @DisplayName("CodeSystem")
    class CodeSystemTests {
      @BeforeEach void beforeeach() {
        loggerSpy.reset();
      }

      @Nested
      @DisplayName("#csMetadata")
      class CsMetadataTests {
        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should parse the simplest possible code system")
        void parseSimplestPossibleCodeSystem() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals("ZOO", codeSystem.getId());
          assertToEqual(List.of(), codeSystem.rules);
          assertToEqual(new TextLocation(2, 1, 2, 15), codeSystem.sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should parse a code system with additional metadata")
        void parseCodeSystemAdditionalMetadata() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        Id: zoo-codes\n"
                                     + "        Title: \"Zoo Animals\"\n"
                                     + "        Description: \"Animals and cryptids that may be at a zoo.\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals("zoo-codes", codeSystem.getId());
          assertEquals("Zoo Animals", codeSystem.title);
          assertEquals("Animals and cryptids that may be at a zoo.", codeSystem.description);
          assertToEqual(List.of(), codeSystem.rules);
          assertToEqual(new TextLocation(2, 1, 5, 57), codeSystem.sourceInfo.location);
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should parse numeric code system name and id")
        void parseNumericCodeSystemNameId() {
          // NOT recommended, but possible
          var input = leftAlign("\n"
                                     + "        CodeSystem: 123\n"
                                     + "        Id: 456\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("123");
          assertEquals("123", codeSystem.name);
          assertEquals("456", codeSystem.getId());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should parse a code system with a multi-line description")
        void parseCodeSystemMultiLineDescription() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        Id: zoo-codes\n"
                                     + "        Title: \"Zoo Animals\"\n"
                                     + "        Description: \"\"\"\n"
                                     + "        Animals that may be present at the zoo. This includes\n"
                                     + "        animals that have been classified by biologists, as\n"
                                     + "        well as certain cryptids, such as:\n"
                                     + "        * quadrapedal cryptids\n"
                                     + "          * jackalope\n"
                                     + "          * hodag\n"
                                     + "        * bipedal cryptids\n"
                                     + "          * swamp ape\n"
                                     + "          * hopkinsville goblin\n"
                                     + "        \"\"\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          var expectedDescription = String.join("\n", "Animals that may be present at the zoo. This includes",
            "animals that have been classified by biologists, as",
            "well as certain cryptids, such as:",
            "* quadrapedal cryptids",
            "  * jackalope",
            "  * hodag",
            "* bipedal cryptids",
            "  * swamp ape",
            "  * hopkinsville goblin");
          assertEquals("ZOO", codeSystem.name);
          assertEquals("zoo-codes", codeSystem.getId());
          assertEquals("Zoo Animals", codeSystem.title);
          assertEquals(expectedDescription, codeSystem.description);
          assertToEqual(List.of(), codeSystem.rules);
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should only apply each metadata attribute the first time it is declared")
        void onlyApplyEachMetadataAttributeFirst() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        Id: zoo-codes\n"
                                     + "        Title: \"Zoo Animals\"\n"
                                     + "        Description: \"Animals and cryptids that may be at a zoo.\"\n"
                                     + "        Title: \"Duplicate Animals\"\n"
                                     + "        Id: zoo-codes-again\n"
                                     + "        Description: \"Lions and tigers and bears!\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals("zoo-codes", codeSystem.getId());
          assertEquals("Zoo Animals", codeSystem.title);
          assertEquals("Animals and cryptids that may be at a zoo.", codeSystem.description);
          assertToEqual(List.of(), codeSystem.rules);
          assertToEqual(new TextLocation(2, 1, 8, 42), codeSystem.sourceInfo.location);
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should log an error when encountering a duplicate metadata attribute")
        void logErrorEncounteringDuplicateMetadataAttribute() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        Id: zoo-codes\n"
                                     + "        Title: \"Zoo Animals\"\n"
                                     + "        Description: \"Animals and cryptids that may be at a zoo.\"\n"
                                     + "        Title: \"Duplicate Animals\"\n"
                                     + "        Id: zoo-codes-again\n"
                                     + "        Description: \"Lions and tigers and bears!\"\n"
                                     + "        ");
          importSingleText(input, "Zoo.fsh");
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 7\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 8\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should log an error and skip the code system when encountering a code system with a name used by another code system")
        void logErrorSkipCodeSystemEncountering() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: BREAD\n"
                                     + "        Title: \"Known Bread\"\n"
                                     + "\n"
                                     + "        CodeSystem: BREAD\n"
                                     + "        Title: \"Unknown Bread\"\n"
                                     + "        ");
          var result = importSingleText(input, "Bread.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("BREAD");
          assertEquals("Known Bread", codeSystem.title);
          assertTrue(matches(Pattern.compile("code system named BREAD already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Bread\\.fsh.*Line: 5 - 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #csMetadata should log an error and skip the code system when encountering an code system with a name used by another code system in another file")
        void logErrorSkipCodeSystemEncountering2() {
          var input1 = "\n"
                            + "          CodeSystem: SomeCodeSystem\n"
                            + "          Title: \"This CodeSystem\"\n"
                            + "        ";
          var input2 = "\n"
                            + "          CodeSystem: SomeCodeSystem\n"
                            + "          Title: \"That CodeSystem\"\n"
                            + "        ";
          var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"),
            new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.codeSystems.size()).sum());
          var codesystem = result.get(0).codeSystems.get("SomeCodeSystem");
          assertEquals("This CodeSystem", codesystem.title);
          assertTrue(matches(Pattern.compile("code system named SomeCodeSystem already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#concept")
      class ConceptTests {
        @Test
        @DisplayName("FSHImporter CodeSystem #concept should parse a code system with one concept")
        void parseCodeSystemOneConcept() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #lion\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals(1, codeSystem.rules.size());
          assertConceptRule(codeSystem.rules.get(0), "lion", null, null, List.of());
          assertToEqual(new TextLocation(3, 1, 3, 7), codeSystem.rules.get(0).sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #concept should parse a code system with one concept with a display string")
        void parseCodeSystemOneConceptDisplay() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #tiger \"Tiger\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals(1, codeSystem.rules.size());
          assertConceptRule(codeSystem.rules.get(0), "tiger", "Tiger", null, List.of());
          assertToEqual(new TextLocation(3, 1, 3, 16), codeSystem.rules.get(0).sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #concept should parse a code system with one concept with display and definition strings")
        void parseCodeSystemOneConceptDisplay2() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #bear \"Bear\" \"A member of family Ursidae.\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals(1, codeSystem.rules.size());
          assertConceptRule(codeSystem.rules.get(0), "bear", "Bear", "A member of family Ursidae.", List.of());
          assertToEqual(new TextLocation(3, 1, 3, 44), codeSystem.rules.get(0).sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #concept should parse a concept with a multi-line definition string")
        void parseConceptMultiLineDefinitionString() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #gorilla \"Gorilla\" \"\"\"\n"
                                     + "        Let there be no mistake\n"
                                     + "        about the greatest ape of all.\n"
                                     + "        \"\"\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals(1, codeSystem.rules.size());
          var expectedDefinition = String.join("\n", "Let there be no mistake",
            "about the greatest ape of all.");
          assertConceptRule(codeSystem.rules.get(0), "gorilla", "Gorilla", expectedDefinition, List.of());
          assertToEqual(new TextLocation(3, 1, 3, 83), codeSystem.rules.get(0).sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #concept should parse a code system with more than one concept")
        void parseCodeSystemMoreThanOne() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #lion\n"
                                     + "        * #tiger \"Tiger\"\n"
                                     + "        * #bear \"Bear\" \"A member of family Ursidae.\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals(3, codeSystem.rules.size());
          assertConceptRule(codeSystem.rules.get(0), "lion", null, null, List.of());
          assertToEqual(new TextLocation(3, 1, 3, 7), codeSystem.rules.get(0).sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertConceptRule(codeSystem.rules.get(1), "tiger", "Tiger", null, List.of());
          assertToEqual(new TextLocation(4, 1, 4, 16), codeSystem.rules.get(1).sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.rules.get(1).sourceInfo.file);
          assertConceptRule(codeSystem.rules.get(2), "bear", "Bear", "A member of family Ursidae.", List.of());
          assertToEqual(new TextLocation(5, 1, 5, 44), codeSystem.rules.get(2).sourceInfo.location);
          assertEquals("Zoo.fsh", codeSystem.rules.get(2).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #concept should parse a code system with hierarchical codes")
        void parseCodeSystemHierarchicalCodes() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #bear \"Bear\" \"A member of family Ursidae.\"\n"
                                     + "        * #bear #sunbear \"Sun bear\" \"Helarctos malayanus\"\n"
                                     + "        * #bear #sunbear #ursula \"Ursula the sun bear\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals(3, codeSystem.rules.size());
          assertConceptRule(codeSystem.rules.get(0), "bear", "Bear", "A member of family Ursidae.", List.of());
          assertToEqual(new TextLocation(3, 1, 3, 44), codeSystem.rules.get(0).sourceInfo.location);
          assertConceptRule(codeSystem.rules.get(1), "sunbear", "Sun bear", "Helarctos malayanus", List.of("bear"));
          assertToEqual(new TextLocation(4, 1, 4, 49), codeSystem.rules.get(1).sourceInfo.location);
          assertConceptRule(codeSystem.rules.get(2), "ursula", "Ursula the sun bear", null, List.of("bear",
            "sunbear"));
          assertToEqual(new TextLocation(5, 1, 5, 46), codeSystem.rules.get(2).sourceInfo.location);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #concept should log an error when a concept includes a system declaration")
        void logErrorConceptIncludesSystemDeclaration() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #goat\n"
                                     + "        * ZOO#bat\n"
                                     + "        * CRYPTID#jackalope\n"
                                     + "        * ZOO#goat #mountaingoat\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.codeSystems.size());
          var codeSystem = result.codeSystems.get("ZOO");
          assertEquals("ZOO", codeSystem.name);
          assertEquals(4, codeSystem.rules.size());
          assertEquals("goat", (((ConceptRule) codeSystem.rules.get(0))).code);
          assertEquals("bat", (((ConceptRule) codeSystem.rules.get(1))).code);
          assertEquals("jackalope", (((ConceptRule) codeSystem.rules.get(2))).code);
          assertEquals("mountaingoat", (((ConceptRule) codeSystem.rules.get(3))).code);
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 5\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#CaretValueRule")
      class CaretValueRuleTests {
        @Test
        @DisplayName("FSHImporter CodeSystem #CaretValueRule should parse a code system that uses a CaretValueRule with no codes")
        void parseCodeSystemUsesCaretValueRuleNo() {
          var input = leftAlign("\n"
                                     + "          CodeSystem: ZOO\n"
                                     + "          * ^publisher = \"Matt\"\n"
                                     + "          ");
          var result = importSingleText(input);
          var codeSystem = result.codeSystems.get("ZOO");
          assertCaretValueRule(((Rule) codeSystem.rules.get(0)), "", "publisher", "Matt", false, List.of());
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #CaretValueRule should parse a code system that uses CaretValueRules with no codes alongside rules")
        void parseCodeSystemUsesCaretValueRulesNo() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #lion\n"
                                     + "        * ^publisher = \"Damon\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          var codeSystem = result.codeSystems.get("ZOO");
          assertConceptRule(codeSystem.rules.get(0), "lion", null, null, List.of());
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertCaretValueRule(
            ((CaretValueRule) codeSystem.rules.get(1)),
            "",
            "publisher",
            "Damon",
            false,
            List.of()
          );
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #CaretValueRule should parse a code system that uses a CaretValueRule on a top-level concept")
        void parseCodeSystemUsesCaretValueRuleTop() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #anteater \"Anteater\"\n"
                                     + "        * #anteater ^property[0].valueString = \"Their threat pose is really cute.\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          var codeSystem = result.codeSystems.get("ZOO");
          assertConceptRule(codeSystem.rules.get(0), "anteater", "Anteater", null, List.of());
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertCaretValueRule(
            codeSystem.rules.get(1),
            "",
            "property[0].valueString",
            "Their threat pose is really cute.",
            false,
            List.of("#anteater")
          );
          assertEquals("Zoo.fsh", codeSystem.rules.get(1).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #CaretValueRule should parse a code system that uses a CaretValueRule on a nested concept")
        void parseCodeSystemUsesCaretValueRuleNested() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #anteater \"Anteater\"\n"
                                     + "        * #anteater #northern \"Northern tamandua\"\n"
                                     + "        * #anteater #northern ^property[0].valueString = \"They are strong climbers.\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          var codeSystem = result.codeSystems.get("ZOO");
          assertConceptRule(codeSystem.rules.get(0), "anteater", "Anteater", null, List.of());
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertConceptRule(codeSystem.rules.get(1), "northern", "Northern tamandua", null, List.of("anteater"));
          assertEquals("Zoo.fsh", codeSystem.rules.get(1).sourceInfo.file);
          assertCaretValueRule(
            codeSystem.rules.get(2),
            "",
            "property[0].valueString",
            "They are strong climbers.",
            false,
            List.of("#anteater", "#northern")
          );
          assertEquals("Zoo.fsh", codeSystem.rules.get(2).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #CaretValueRule should keep the raw value of a code caret value rule when the value is a number or boolean")
        void keepRawValueCodeCaretValue() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: ZOO\n"
                                     + "        * #anteater \"Anteater\"\n"
                                     + "        * #anteater ^extension[0].valueInteger = 0.4500\n"
                                     + "        * #anteater ^extension[1].valueBoolean = true\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          var codeSystem = result.codeSystems.get("ZOO");
          assertConceptRule(codeSystem.rules.get(0), "anteater", "Anteater", null, List.of());
          assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
          assertCaretValueRule(
            codeSystem.rules.get(1),
            "",
            "extension[0].valueInteger",
            0.45,
            false,
            List.of("#anteater"),
            "0.4500"
          );
          assertEquals("Zoo.fsh", codeSystem.rules.get(1).sourceInfo.file);
          assertCaretValueRule(
            codeSystem.rules.get(2),
            "",
            "extension[1].valueBoolean",
            true,
            false,
            List.of("#anteater"),
            "true"
          );
          assertEquals("Zoo.fsh", codeSystem.rules.get(2).sourceInfo.file);
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter CodeSystem #insertRule should parse an insert rule with a single RuleSet")
        void parseInsertRuleSingleRuleSet() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: MyCS\n"
                                     + "        * insert MyRuleSet\n"
                                     + "        ");
          var result = importSingleText(input, "Insert.fsh");
          var cs = result.codeSystems.get("MyCS");
          assertEquals(1, cs.rules.size());
          assertInsertRule(((InsertRule) cs.rules.get(0)), "", "MyRuleSet");
        }

        @Test
        @DisplayName("FSHImporter CodeSystem #insertRule should parse an insert rule with a single RuleSet and a code path")
        void parseInsertRuleSingleRuleSetCode() {
          var input = leftAlign("\n"
                                     + "        CodeSystem: MyCS\n"
                                     + "        * #cookie \"Cookie\"\n"
                                     + "        * #cookie insert MyRuleSet\n"
                                     + "        ");
          var result = importSingleText(input, "Insert.fsh");
          var cs = result.codeSystems.get("MyCS");
          assertEquals(2, cs.rules.size());
          assertInsertRule(((InsertRule) cs.rules.get(1)), "", "MyRuleSet", List.of(), List.of("#cookie"));
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
