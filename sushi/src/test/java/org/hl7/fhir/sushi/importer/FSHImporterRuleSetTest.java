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

// port of sushi/test/import/FSHImporter.RuleSet.test.ts (SUSHI 3.21.1)
class FSHImporterRuleSetTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void beforeeach() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("RuleSet")
    class RuleSetTests {
      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with a rule")
      void parseRuleSetRule() {
        var input = "\n"
                         + "        RuleSet: OneRuleSet\n"
                         + "        * active = true\n"
                         + "        ";
        var result = importSingleText(input, "OneRule.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("OneRuleSet");
        assertEquals("OneRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 3, 23), ruleSet.sourceInfo.location);
        assertEquals("OneRule.fsh", ruleSet.sourceInfo.file);
        assertAssignmentRule(((Rule) ruleSet.rules.get(0)), "active", true);
      }

      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with a numeric name")
      void parseRuleSetNumericName() {
        var input = "\n"
                         + "        RuleSet: 123\n"
                         + "        * active = true\n"
                         + "        ";
        var result = importSingleText(input, "NumericName.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("123");
        assertEquals("123", ruleSet.name);
      }

      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with multiple rules")
      void parseRuleSetMultipleRules() {
        var input = "\n"
                         + "        RuleSet: RuleRuleSet\n"
                         + "        * gender from https://www.hl7.org/fhir/valueset-administrative-gender.html\n"
                         + "        * active = true (exactly)\n"
                         + "        * contact 1..1\n"
                         + "        ";
        var result = importSingleText(input, "Rules.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("RuleRuleSet");
        assertEquals("RuleRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 5, 22), ruleSet.sourceInfo.location);
        assertBindingRule(
          ((Rule) ruleSet.rules.get(0)),
          "gender",
          "https://www.hl7.org/fhir/valueset-administrative-gender.html",
          "required"
        );
        assertAssignmentRule(((Rule) ruleSet.rules.get(1)), "active", true, true);
        assertCardRule(((Rule) ruleSet.rules.get(2)), "contact", 1, "1");
      }

      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with an insert rule")
      void parseRuleSetInsertRule() {
        var input = "\n"
                         + "        RuleSet: RuleRuleSet\n"
                         + "        * gender from https://www.hl7.org/fhir/valueset-administrative-gender.html\n"
                         + "        * insert OtherRuleSet\n"
                         + "        * contact 1..1\n"
                         + "        ";
        var result = importSingleText(input, "Rules.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("RuleRuleSet");
        assertEquals("RuleRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 5, 22), ruleSet.sourceInfo.location);
        assertBindingRule(
          ((Rule) ruleSet.rules.get(0)),
          "gender",
          "https://www.hl7.org/fhir/valueset-administrative-gender.html",
          "required"
        );
        assertInsertRule(((Rule) ruleSet.rules.get(1)), "", "OtherRuleSet");
        assertCardRule(((Rule) ruleSet.rules.get(2)), "contact", 1, "1");
      }

      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with an AddElementRule")
      void parseRuleSetAddElementRule() {
        var input = "\n"
                         + "        RuleSet: RuleRuleSet\n"
                         + "        * gender from https://www.hl7.org/fhir/valueset-administrative-gender.html\n"
                         + "        * contact 1..1\n"
                         + "        * newStuff 0..* string \"short for newStuff property\"\n"
                         + "        ";
        var result = importSingleText(input, "Rules.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("RuleRuleSet");
        assertEquals("RuleRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 5, 60), ruleSet.sourceInfo.location);
        assertBindingRule(
          ((Rule) ruleSet.rules.get(0)),
          "gender",
          "https://www.hl7.org/fhir/valueset-administrative-gender.html",
          "required"
        );
        assertCardRule(((Rule) ruleSet.rules.get(1)), "contact", 1, "1");
        assertAddElementRule(ruleSet.rules.get(2), "newStuff", new AddElementArgs(0, "*", null, List.of(ort("string")), new Defs("short for newStuff property", "short for newStuff property", null)));
      }

      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with a content reference AddElementRule")
      void parseRuleSetContentReferenceAddElementRule() {
        var input = "\n"
                         + "        RuleSet: RuleRuleSet\n"
                         + "        * gender from https://www.hl7.org/fhir/valueset-administrative-gender.html\n"
                         + "        * contact 1..1\n"
                         + "        * newStuff 0..3 contentReference http://example.org/StructureDefinition/Stuff#Stuff.new \"short for newStuff property\"\n"
                         + "      ";
        var result = importSingleText(input, "Rules.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("RuleRuleSet");
        assertEquals("RuleRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 5, 125), ruleSet.sourceInfo.location);
        assertBindingRule(
          ((Rule) ruleSet.rules.get(0)),
          "gender",
          "https://www.hl7.org/fhir/valueset-administrative-gender.html",
          "required"
        );
        assertCardRule(((Rule) ruleSet.rules.get(1)), "contact", 1, "1");
        assertAddElementRule(ruleSet.rules.get(2), "newStuff", new AddElementArgs(0, "3", null, List.of(), new Defs("short for newStuff property", "short for newStuff property", "http://example.org/StructureDefinition/Stuff#Stuff.new")));
      }

      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with a MappingRule")
      void parseRuleSetMappingRule() {
        var input = "\n"
                         + "        RuleSet: OneRuleSet\n"
                         + "        * identifier.system -> \"Patient.identifier.system\"\n"
                         + "        * identifier.value -> \"Patient.identifier.value\" \"This is a comment\" #code\n"
                         + "        ";
        var result = importSingleText(input, "OneRule.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("OneRuleSet");
        assertEquals("OneRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 4, 82), ruleSet.sourceInfo.location);
        assertEquals("OneRule.fsh", ruleSet.sourceInfo.file);
        assertMappingRule(
          ((Rule) ruleSet.rules.get(0)),
          "identifier.system",
          "Patient.identifier.system",
          null,
          null
        );
        assertMappingRule(
          ((Rule) ruleSet.rules.get(1)),
          "identifier.value",
          "Patient.identifier.value",
          "This is a comment",
          new FshCode("code").withFile("OneRule.fsh").withLocation(new int[] {4, 78, 4, 82})
        );
      }

      @Test
      @DisplayName("FSHImporter RuleSet should parse a RuleSet with rules, ValueSetComponents, ConceptRules, and CaretValueRules")
      void parseRuleSetRulesValueSetComponentsConceptRulesCaretValueRules() {
        var input = "\n"
                         + "        RuleSet: RuleRuleSet\n"
                         + "        * gender from https://www.hl7.org/fhir/valueset-administrative-gender.html\n"
                         + "        * #bear from system ZOO\n"
                         + "        * #lion\n"
                         + "        * #lion ^designation.value = \"Watch out for big cat!\"\n"
                         + "        ";
        var result = importSingleText(input, "Rules.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("RuleRuleSet");
        assertEquals("RuleRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 6, 61), ruleSet.sourceInfo.location);
        assertEquals(4, ruleSet.rules.size());
        assertBindingRule(
          ((Rule) ruleSet.rules.get(0)),
          "gender",
          "https://www.hl7.org/fhir/valueset-administrative-gender.html",
          "required"
        );
        assertValueSetConceptComponent(ruleSet.rules.get(1), "ZOO", null, List.of(new FshCode("bear", "ZOO").withLocation(new int[] {4, 11, 4, 15}).withFile("Rules.fsh")));
        var concept = ruleSet.rules.get(2);
        assertToEqual(new ConceptRule("lion").withFile("Rules.fsh").withLocation(new int[] {5, 9, 5, 15}), concept);
        assertCaretValueRule(
          ruleSet.rules.get(3),
          "",
          "designation.value",
          "Watch out for big cat!",
          false,
          List.of("#lion")
        );
        assertToEqual(new TextLocation(6, 9, 6, 61), ruleSet.rules.get(3).sourceInfo.location);
      }

      @Test
      @DisplayName("FSHImporter RuleSet should log an error when parsing a mixin with no rules")
      void logErrorParsingMixinNoRules() {
        var input = "\n"
                         + "        RuleSet: EmptyRuleSet\n"
                         + "        ";
        var result = importSingleText(input, "Empty.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("EmptyRuleSet");
        assertEquals("EmptyRuleSet", ruleSet.name);
        assertToEqual(new TextLocation(2, 9, 2, 29), ruleSet.sourceInfo.location);
        assertTrue(matches(Pattern.compile("File: Empty\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter RuleSet should log an error and skip the RuleSet when encountering a RuleSet with a name used by another RuleSet")
      void logErrorSkipRuleSetEncounteringRuleSet() {
        var input = "\n"
                         + "      RuleSet: SameRuleSet\n"
                         + "      * gender 0..0\n"
                         + "\n"
                         + "      RuleSet: SameRuleSet\n"
                         + "      * active = true\n"
                         + "      ";
        var result = importSingleText(input, "SameName.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("SameRuleSet");
        assertEquals(1, ruleSet.rules.size());
        assertCardRule(((Rule) ruleSet.rules.get(0)), "gender", 0, "0");
        assertTrue(matches(Pattern.compile("RuleSet named SameRuleSet already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 5 - 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter RuleSet should log an error and skip the ruleset when encountering an ruleset with a name used by another ruleset in another file")
      void logErrorSkipRulesetEncounteringRuleset() {
        var input1 = "\n"
                          + "        RuleSet: SameRuleSet\n"
                          + "        * gender 0..0\n"
                          + "      ";
        var input2 = "\n"
                          + "        RuleSet: SameRuleSet\n"
                          + "        * active = true\n"
                          + "      ";
        var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"), new RawFSH(input2, "File2.fsh")));
        assertEquals(1, result.stream().mapToInt(d2 -> d2.ruleSets.size()).sum());
        var r = result.get(0).ruleSets.get("SameRuleSet");
        assertCardRule(((Rule) r.rules.get(0)), "gender", 0, "0");
        assertTrue(matches(Pattern.compile("RuleSet named SameRuleSet already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter RuleSet should not log an error when the ConceptRule has one code with a system, no definition, and no hierarchy")
      void notLogErrorConceptRuleHasOne() {
        var input = leftAlign("\n"
                                   + "      RuleSet: VSRuleSet\n"
                                   + "      * ZOO#bear\n"
                                   + "      * ZOO#gator \"Alligator\"\n"
                                   + "      ");
        var result = importSingleText(input, "VSRules.fsh");
        assertEquals(1, result.ruleSets.size());
        var ruleSet = result.ruleSets.get("VSRuleSet");
        assertEquals(2, ruleSet.rules.size());
        assertConceptRule(ruleSet.rules.get(0), "bear", null, null, List.of());
        assertConceptRule(ruleSet.rules.get(1), "gator", "Alligator", null, List.of());
        assertTrue(loggerSpy.getAllMessages("error").isEmpty());
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
