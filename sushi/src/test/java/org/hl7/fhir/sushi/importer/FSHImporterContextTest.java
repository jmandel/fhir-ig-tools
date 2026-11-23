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

// port of sushi/test/import/FSHImporter.context.test.ts (SUSHI 3.21.1)
class FSHImporterContextTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void beforeeach() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("context")
    class ContextTests {
      @Test
      @DisplayName("FSHImporter context should parse non-indented rules")
      void parseNonIndentedRules() {
        var input = leftAlign("\n"
                                   + "        Instance: Foo\n"
                                   + "        InstanceOf: Patient\n"
                                   + "        * name.family = \"foo\"\n"
                                   + "        * name.given = \"bar\"\n"
                                   + "      ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertAssignmentRule(instance.rules.get(0), "name.family", "foo");
        assertAssignmentRule(instance.rules.get(1), "name.given", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should parse a rule that has context while ignoring whitespace lines")
      void parseRuleHasContextWhileIgnoring() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Patient\n"
                                   + "      * name.family = \"foo\"\n"
                                   + "\n"
                                   + "        * id = \"foo-id\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertAssignmentRule(instance.rules.get(0), "name.family", "foo");
        assertAssignmentRule(instance.rules.get(1), "name.family.id", "foo-id");
      }

      @Test
      @DisplayName("FSHImporter context should parse a rule that has context")
      void parseRuleHasContext() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Patient\n"
                                   + "      * name.family = \"foo\"\n"
                                   + "        * id = \"foo-id\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertAssignmentRule(instance.rules.get(0), "name.family", "foo");
        assertAssignmentRule(instance.rules.get(1), "name.family.id", "foo-id");
      }

      @Test
      @DisplayName("FSHImporter context should parse multiple rules at the same level of context")
      void parseMultipleRulesAtSameLevel() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name 1..1\n"
                                   + "        * family 1..1\n"
                                   + "        * given 1..1\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(3, profile.rules.size());
        assertCardRule(profile.rules.get(0), "name", 1, 1);
        assertCardRule(profile.rules.get(1), "name.family", 1, 1);
        assertCardRule(profile.rules.get(2), "name.given", 1, 1);
      }

      @Test
      @DisplayName("FSHImporter context should preserve higher level context when decreasing indent")
      void preserveHigherLevelContextDecreasingIndent() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name 1..1\n"
                                   + "        * family 1..1\n"
                                   + "          * id 1..1\n"
                                   + "            * id 1..1\n"
                                   + "        * given 1..1\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(5, profile.rules.size());
        assertCardRule(profile.rules.get(0), "name", 1, 1);
        assertCardRule(profile.rules.get(1), "name.family", 1, 1);
        assertCardRule(profile.rules.get(2), "name.family.id", 1, 1);
        assertCardRule(profile.rules.get(3), "name.family.id.id", 1, 1);
        assertCardRule(profile.rules.get(4), "name.given", 1, 1);
      }

      @Test
      @DisplayName("FSHImporter context should parse a rule that only sets a path context on a StructureDefinition")
      void parseRuleOnlySetsPathContext() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        // Path Rules are not added to the rules array in Profiles
        assertEquals(0, profile.rules.size());
      }

      @Test
      @DisplayName("FSHImporter context should parse a rule that only sets a path context on an Instance")
      void parseRuleOnlySetsPathContext2() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Patient\n"
                                   + "      * name\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        // Path Rules are added to the rules array in Instances
        assertEquals(1, instance.rules.size());
        assertPathRule(instance.rules.get(0), "name");
      }

      @Test
      @DisplayName("FSHImporter context should use context from rules that only set context on StructureDefinitions")
      void useContextFromRulesOnlySet() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name\n"
                                   + "        * family 1..1\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(1, profile.rules.size());
        assertCardRule(profile.rules.get(0), "name.family", 1, 1);
      }

      @Test
      @DisplayName("FSHImporter context should use context from rules that only set context on Instances")
      void useContextFromRulesOnlySet2() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Patient\n"
                                   + "      * name\n"
                                   + "        * family = \"Foo\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertPathRule(instance.rules.get(0), "name");
        assertAssignmentRule(instance.rules.get(1), "name.family", "Foo");
      }

      @Test
      @DisplayName("FSHImporter context should change + to = when setting context on children of soft indexed rules on StructureDefs")
      void changeSettingContextChildrenSoftIndexed() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * linkId = \"foo\"\n"
                                   + "        * item[+]\n"
                                   + "          * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Questionnaire", profile.parent);
        assertEquals(2, profile.rules.size());
        assertAssignmentRule(profile.rules.get(0), "item[+].linkId", "foo");
        assertAssignmentRule(profile.rules.get(1), "item[=].item[+].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should change + to = when setting context on children of soft indexed rules on Instances")
      void changeSettingContextChildrenSoftIndexed2() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * linkId = \"foo\"\n"
                                   + "        * item[+]\n"
                                   + "          * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Questionnaire", instance.instanceOf);
        assertEquals(4, instance.rules.size());
        assertPathRule(instance.rules.get(0), "item[+]");
        assertAssignmentRule(instance.rules.get(1), "item[=].linkId", "foo");
        assertPathRule(instance.rules.get(2), "item[=].item[+]");
        assertAssignmentRule(instance.rules.get(3), "item[=].item[=].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should keep + on consecutive path rules when setting context on first child of soft indexed rules on StructureDefinitions")
      void keepConsecutivePathRulesSettingContext() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * item[+]\n"
                                   + "          * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Questionnaire", profile.parent);
        assertEquals(1, profile.rules.size());
        assertAssignmentRule(profile.rules.get(0), "item[+].item[+].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should change + to = on consecutive path rules when setting context on first child of soft indexed rules on Instances")
      void changeConsecutivePathRulesSettingContext() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * item[+]\n"
                                   + "          * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Questionnaire", instance.instanceOf);
        assertEquals(3, instance.rules.size());
        assertPathRule(instance.rules.get(0), "item[+]");
        assertPathRule(instance.rules.get(1), "item[=].item[+]");
        assertAssignmentRule(instance.rules.get(2), "item[=].item[=].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should log a warning for path rules that increment the index but then do not use it on StructureDefinitions")
      void logWarningForPathRulesIncrement() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "      * item[+]\n"
                                   + "        * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertTrue(matches(Pattern.compile("the previous path context will be ignored(.|\\s)*Line: 5$"), loggerSpy.getLastMessage("warn")));
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Questionnaire", profile.parent);
        assertEquals(1, profile.rules.size());
        assertAssignmentRule(profile.rules.get(0), "item[+].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should parse path rules that increment the index but then do not use it on Instances")
      void parsePathRulesIncrementIndexBut() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "      * item[+]\n"
                                   + "        * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Questionnaire", instance.instanceOf);
        assertEquals(3, instance.rules.size());
        assertPathRule(instance.rules.get(0), "item[+]");
        assertPathRule(instance.rules.get(1), "item[+]");
        assertAssignmentRule(instance.rules.get(2), "item[=].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should not log a warning for non-path rules that increment the index")
      void notLogWarningForNonPath() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Questionnaire\n"
                                   + "      * item[+].linkId = \"foo\"\n"
                                   + "      * item[+].linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Questionnaire", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertAssignmentRule(instance.rules.get(0), "item[+].linkId", "foo");
        assertAssignmentRule(instance.rules.get(1), "item[+].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should change + to = when setting context on children of soft indexed rules which are not path rules on StructureDefinitions")
      void changeSettingContextChildrenSoftIndexed3() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * linkId = \"foo\"\n"
                                   + "        * item[+].linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Questionnaire", profile.parent);
        assertEquals(2, profile.rules.size());
        assertAssignmentRule(profile.rules.get(0), "item[+].linkId", "foo");
        assertAssignmentRule(profile.rules.get(1), "item[=].item[+].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should change + to = when setting context on children of soft indexed rules which are not path rules on Instances")
      void changeSettingContextChildrenSoftIndexed4() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * linkId = \"foo\"\n"
                                   + "        * item[+].linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Questionnaire", instance.instanceOf);
        assertEquals(3, instance.rules.size());
        assertPathRule(instance.rules.get(0), "item[+]");
        assertAssignmentRule(instance.rules.get(1), "item[=].linkId", "foo");
        assertAssignmentRule(instance.rules.get(2), "item[=].item[+].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should change + to = when setting context on grandchildren of soft indexed rules which are not path rules on StructureDefinitions")
      void changeSettingContextGrandchildrenSoftIndexed() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * code = #foo\n"
                                   + "          * display = \"Foo\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Questionnaire", profile.parent);
        assertEquals(2, profile.rules.size());
        assertAssignmentRule(
          profile.rules.get(0),
          "item[+].code",
          new FshCode("foo").withFile("Context.fsh").withLocation(new int[] {5, 12, 5, 15})
        );
        assertAssignmentRule(profile.rules.get(1), "item[=].code.display", "Foo");
      }

      @Test
      @DisplayName("FSHImporter context should change + to = when setting context on grandchildren of soft indexed rules which are not path rules on Instances")
      void changeSettingContextGrandchildrenSoftIndexed2() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Questionnaire\n"
                                   + "      * item[+]\n"
                                   + "        * code = #foo\n"
                                   + "          * display = \"Foo\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Questionnaire", instance.instanceOf);
        assertEquals(3, instance.rules.size());
        assertPathRule(instance.rules.get(0), "item[+]");
        assertAssignmentRule(
          instance.rules.get(1),
          "item[=].code",
          new FshCode("foo").withFile("Context.fsh").withLocation(new int[] {5, 12, 5, 15})
        );
        assertAssignmentRule(instance.rules.get(2), "item[=].code.display", "Foo");
      }

      @Test
      @DisplayName("FSHImporter context should change nested + to = when setting context on children of soft indexed rules on StructureDefinitions")
      void changeNestedSettingContextChildrenSoft() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Questionnaire\n"
                                   + "      * item[+].item[+]\n"
                                   + "        * linkId = \"foo\"\n"
                                   + "        * item[+]\n"
                                   + "          * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Questionnaire", profile.parent);
        assertEquals(2, profile.rules.size());
        assertAssignmentRule(profile.rules.get(0), "item[+].item[+].linkId", "foo");
        assertAssignmentRule(profile.rules.get(1), "item[=].item[=].item[+].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should change nested + to = when setting context on children of soft indexed rules on Instances")
      void changeNestedSettingContextChildrenSoft2() {
        var input = leftAlign("\n"
                                   + "      Instance: Foo\n"
                                   + "      InstanceOf: Questionnaire\n"
                                   + "      * item[+].item[+]\n"
                                   + "        * linkId = \"foo\"\n"
                                   + "        * item[+]\n"
                                   + "          * linkId = \"bar\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Questionnaire", instance.instanceOf);
        assertEquals(4, instance.rules.size());
        assertPathRule(instance.rules.get(0), "item[+].item[+]");
        assertAssignmentRule(instance.rules.get(1), "item[=].item[=].linkId", "foo");
        assertPathRule(instance.rules.get(2), "item[=].item[=].item[+]");
        assertAssignmentRule(instance.rules.get(3), "item[=].item[=].item[=].linkId", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should parse child rules that have a blank path")
      void parseChildRulesHaveBlankPath() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name 1..1\n"
                                   + "        * ^short = \"foo\"\n"
                                   + "        * obeys inv1\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(3, profile.rules.size());
        assertCardRule(profile.rules.get(0), "name", 1, 1);
        assertCaretValueRule(profile.rules.get(1), "name", "short", "foo", false, List.of("name"));
        assertObeysRule(profile.rules.get(2), "name", "inv1");
      }

      // TODO: ;
      // 
      //     // Note: there is no equivalent test for Instances because no Instance rules support multiple paths
      @Test
      @DisplayName("FSHImporter context should apply context to multiple paths")
      void applyContextMultiplePaths() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name\n"
                                   + "        * family and given MS\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(2, profile.rules.size());
        assertFlagRule(
          profile.rules.get(0),
          "name.family",
          true,
          null,
          null,
          null,
          null,
          null
        );
        assertFlagRule(
          profile.rules.get(1),
          "name.given",
          true,
          null,
          null,
          null,
          null,
          null
        );
      }

      @Test
      @DisplayName("FSHImporter context should apply the last path in a list when multiple paths are used to set context")
      void applyLastPathListMultiplePaths() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name and birthDate MS\n"
                                   + "        * ^short = \"foo\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(3, profile.rules.size());
        assertFlagRule(
          profile.rules.get(0),
          "name",
          true,
          null,
          null,
          null,
          null,
          null
        );
        assertFlagRule(
          profile.rules.get(1),
          "birthDate",
          true,
          null,
          null,
          null,
          null,
          null
        );
        assertCaretValueRule(profile.rules.get(2), "birthDate", "short", "foo", false, List.of("birthDate"));
      }

      @Test
      @DisplayName("FSHImporter context should apply context to AddElement rules")
      void applyContextAddElementRules() {
        var input = leftAlign("\n"
                                   + "      Logical: Human\n"
                                   + "      * family 0..1 BackboneElement \"Family\"\n"
                                   + "        * mother 0..2 string \"Mother\"\n"
                                   + "        * father 0..2 string \"Father\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.logicals.size());
        var logical = result.logicals.get("Human");
        assertEquals("Human", logical.name);
        assertEquals(3, logical.rules.size());
        assertAddElementRule(logical.rules.get(0), "family", new AddElementArgs(0, "1", null, List.of(ort("BackboneElement")), new Defs("Family", null, null)));
        assertAddElementRule(logical.rules.get(1), "family.mother", new AddElementArgs(0, "2", null, List.of(ort("string")), new Defs("Mother", null, null)));
        assertAddElementRule(logical.rules.get(2), "family.father", new AddElementArgs(0, "2", null, List.of(ort("string")), new Defs("Father", null, null)));
      }

      @Test
      @DisplayName("FSHImporter context should set the context correctly when a rule with . path is followed by a caret rule with no path")
      void setContextCorrectlyRulePathFollowed() {
        var input = leftAlign("\n"
                                   + "      Extension: TestExtension\n"
                                   + "      * value[x] only boolean\n"
                                   + "      * . 0..1\n"
                                   + "        * ^short = \"A test extension.\"\n"
                                   + "        * ^definition = \"Yep.  It is a test extension.\"\n"
                                   + "      * ^context.expression = \"Patient\"\n"
                                   + "      ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.extensions.size());
        var extension = result.extensions.get("TestExtension");
        assertEquals(5, extension.rules.size());
        assertOnlyRule(extension.rules.get(0), "value[x]", ort("boolean"));
        assertCardRule(extension.rules.get(1), ".", 0, "1");
        assertCaretValueRule(extension.rules.get(2), ".", "short", "A test extension.", false, List.of("."));
        assertCaretValueRule(
          extension.rules.get(3),
          ".",
          "definition",
          "Yep.  It is a test extension.",
          false,
          List.of(".")
        );
        assertCaretValueRule(extension.rules.get(4), "", "context.expression", "Patient", false, List.of());
      }

      @Test
      @DisplayName("FSHImporter context should log an error when a . rule is indented")
      void logErrorRuleIndented() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * name 1..1\n"
                                   + "        * . ^short = \"foo\"\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("The special '\\.' path"), loggerSpy.getLastMessage("error")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(2, profile.rules.size());
        assertCardRule(profile.rules.get(0), "name", 1, 1);
        assertCaretValueRule(profile.rules.get(1), ".", "short", "foo", false, List.of("."));
      }

      @Test
      @DisplayName("FSHImporter context should log an error when a rule is indented below a rule without a path")
      void logErrorRuleIndentedBelowRule() {
        var input = leftAlign("\n"
                                   + "      Profile: Foo\n"
                                   + "      Parent: Patient\n"
                                   + "      * ^url = \"http://example.org\"\n"
                                   + "        * name 1..1\n"
                                   + "    ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("Rule cannot be indented"), loggerSpy.getLastMessage("error")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.profiles.size());
        var profile = result.profiles.get("Foo");
        assertEquals("Foo", profile.name);
        assertEquals("Patient", profile.parent);
        assertEquals(2, profile.rules.size());
        assertCaretValueRule(profile.rules.get(0), "", "url", "http://example.org", false);
        assertCardRule(profile.rules.get(1), "name", 1, 1);
      }

      @Test
      @DisplayName("FSHImporter context should log an error when a rule is indented an invalid amount of spaces")
      void logErrorRuleIndentedInvalidAmount() {
        // only 1 space of indent
        var input = leftAlign("\n"
                                   + "        Instance: Foo\n"
                                   + "        InstanceOf: Patient\n"
                                   + "        * name.family = \"foo\"\n"
                                   + "         * id = \"bar\"\n"
                                   + "      ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("Unable to determine path context.*1 space"), loggerSpy.getLastMessage("error")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertAssignmentRule(instance.rules.get(0), "name.family", "foo");
        // rule is not assigned any context
        assertAssignmentRule(instance.rules.get(1), "id", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should log an error when the first rule of a definition is indented")
      void logErrorFirstRuleDefinitionIndented() {
        var input = leftAlign("\n"
                                   + "        Instance: Foo\n"
                                   + "        InstanceOf: Patient\n"
                                   + "          * name.family = \"foo\"\n"
                                   + "        * id = \"bar\"\n"
                                   + "      ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("first rule of a definition must be left-aligned"), loggerSpy.getLastMessage("error")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertAssignmentRule(instance.rules.get(0), "name.family", "foo");
        // rule is not assigned any context
        assertAssignmentRule(instance.rules.get(1), "id", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should log an error when a rule is indented too deeply")
      void logErrorRuleIndentedTooDeeply() {
        // 4 spaces of indent
        var input = leftAlign("\n"
                                   + "        Instance: Foo\n"
                                   + "        InstanceOf: Patient\n"
                                   + "        * name.family = \"foo\"\n"
                                   + "            * id = \"bar\"\n"
                                   + "      ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("indented too deeply"), loggerSpy.getLastMessage("error")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.instances.size());
        var instance = result.instances.get("Foo");
        assertEquals("Foo", instance.name);
        assertEquals("Patient", instance.instanceOf);
        assertEquals(2, instance.rules.size());
        assertAssignmentRule(instance.rules.get(0), "name.family", "foo");
        // rule is not assigned any context
        assertAssignmentRule(instance.rules.get(1), "id", "bar");
      }

      @Test
      @DisplayName("FSHImporter context should log an error when a ValueSetFilterComponentRule is indented")
      void logErrorValueSetFilterComponentRuleIndented() {
        var input = leftAlign("\n"
                                   + "        ValueSet: Foo\n"
                                   + "        * include codes from system http://example.org\n"
                                   + "          * include codes from system http://indent.org\n"
                                   + "      ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("rule that does not use a path cannot be indented"), loggerSpy.getLastMessage("error")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.valueSets.size());
        var vs = result.valueSets.get("Foo");
        assertEquals("Foo", vs.name);
        assertEquals(2, vs.rules.size());
        assertValueSetFilterComponent(vs.rules.get(0), "http://example.org", null, List.of());
        // rule is not assigned any context
        assertValueSetFilterComponent(vs.rules.get(1), "http://indent.org", null, List.of());
      }

      @Test
      @DisplayName("FSHImporter context should log an error when a ValueSetConceptComponentRule is indented")
      void logErrorValueSetConceptComponentRuleIndented() {
        var input = leftAlign("\n"
                                   + "        ValueSet: Foo\n"
                                   + "        * http://example.org#foo\n"
                                   + "          * http://example.org#bar\n"
                                   + "      ");
        var result = importSingleText(input, "Context.fsh");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("rule that does not use a path cannot be indented"), loggerSpy.getLastMessage("error")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, result.valueSets.size());
        var vs = result.valueSets.get("Foo");
        assertEquals("Foo", vs.name);
        assertEquals(1, vs.rules.size());
        assertValueSetConceptComponent(vs.rules.get(0), "http://example.org", null, List.of(new FshCode("foo", "http://example.org")
            .withLocation(new int[] {3, 3, 3, 24})
            .withFile("Context.fsh"),
          new FshCode("bar", "http://example.org").withLocation(new int[] {4, 5, 4, 26}).withFile("Context.fsh")));
      }

    }

    @Nested
    @DisplayName("code context")
    class CodeContextTests {
      @Test
      @DisplayName("FSHImporter code context should parse a code system with indented hierarchical rules")
      void parseCodeSystemIndentedHierarchicalRules() {
        var input = leftAlign("\n"
                                   + "      CodeSystem: ZOO\n"
                                   + "      * #bear \"Bear\" \"A member of family Ursidae.\"\n"
                                   + "        * #sunbear \"Sun bear\" \"Helarctos malayanus\"\n"
                                   + "          * #ursula \"Ursula the sun bear\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.codeSystems.size());
        var codeSystem = result.codeSystems.get("ZOO");
        assertEquals("ZOO", codeSystem.name);
        assertEquals(3, codeSystem.rules.size());
        assertConceptRule(codeSystem.rules.get(0), "bear", "Bear", "A member of family Ursidae.", List.of());
        assertToEqual(new TextLocation(3, 1, 3, 44), codeSystem.rules.get(0).sourceInfo.location);
        assertConceptRule(codeSystem.rules.get(1), "sunbear", "Sun bear", "Helarctos malayanus", List.of("bear"));
        assertToEqual(new TextLocation(4, 3, 4, 45), codeSystem.rules.get(1).sourceInfo.location);
        assertConceptRule(codeSystem.rules.get(2), "ursula", "Ursula the sun bear", null, List.of("bear",
          "sunbear"));
        assertToEqual(new TextLocation(5, 5, 5, 35), codeSystem.rules.get(2).sourceInfo.location);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should parse a code system that uses an indented CaretValueRule on a top-level concept")
      void parseCodeSystemUsesIndentedCaretValueRule() {
        var input = leftAlign("\n"
                                   + "      CodeSystem: ZOO\n"
                                   + "      * #anteater \"Anteater\"\n"
                                   + "        * ^property[0].valueString = \"Their threat pose is really cute.\"\n"
                                   + "      ");
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
      @DisplayName("FSHImporter code context should parse a code system that uses an indented CaretValueRule on a nested concept")
      void parseCodeSystemUsesIndentedCaretValueRule2() {
        var input = leftAlign("\n"
                                   + "      CodeSystem: ZOO\n"
                                   + "      * #anteater \"Anteater\"\n"
                                   + "        * #northern \"Northern tamandua\"\n"
                                   + "          * ^property[0].valueString = \"They are strong climbers.\"\n"
                                   + "      ");
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
      @DisplayName("FSHImporter code context should parse a code system that uses an indented InsertRule on a top-level concept")
      void parseCodeSystemUsesIndentedInsertRule() {
        var input = leftAlign("\n"
                                   + "      CodeSystem: ZOO\n"
                                   + "      * #anteater \"Anteater\"\n"
                                   + "        * insert MyRuleSet\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        var codeSystem = result.codeSystems.get("ZOO");
        assertEquals(2, codeSystem.rules.size());
        assertConceptRule(codeSystem.rules.get(0), "anteater", "Anteater", null, List.of());
        assertInsertRule(codeSystem.rules.get(1), "", "MyRuleSet", List.of(), List.of("#anteater"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should allow code path context to be set by referencing an existing top-level code")
      void allowCodePathContextSetBy() {
        var input = leftAlign("\n"
                                   + "      CodeSystem: ZOO\n"
                                   + "      * #anteater \"Anteater\"\n"
                                   + "        * #northern \"Northern tamandua\"\n"
                                   + "          * ^property[0].valueString = \"They are strong climbers.\"\n"
                                   + "      * #anteater\n"
                                   + "        * ^property[0].valueString = \"Their threat pose is really cute.\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        var codeSystem = result.codeSystems.get("ZOO");
        assertEquals(5, codeSystem.rules.size());
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
        assertConceptRule(codeSystem.rules.get(3), "anteater", null, null, List.of());
        assertEquals("Zoo.fsh", codeSystem.rules.get(3).sourceInfo.file);
        assertCaretValueRule(
          codeSystem.rules.get(4),
          "",
          "property[0].valueString",
          "Their threat pose is really cute.",
          false,
          List.of("#anteater")
        );
        assertEquals("Zoo.fsh", codeSystem.rules.get(4).sourceInfo.file);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should allow code path context to be set by referencing an existing nested code")
      void allowCodePathContextSetBy2() {
        var input = leftAlign("\n"
                                   + "      CodeSystem: ZOO\n"
                                   + "      * #anteater \"Anteater\"\n"
                                   + "        * #northern \"Northern tamandua\"\n"
                                   + "        * #southern \"Southern tamandua\"\n"
                                   + "      * #anteater #northern\n"
                                   + "        * ^property[0].valueString = \"They are strong climbers.\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        var codeSystem = result.codeSystems.get("ZOO");
        assertEquals(5, codeSystem.rules.size());
        assertConceptRule(codeSystem.rules.get(0), "anteater", "Anteater", null, List.of());
        assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
        assertConceptRule(codeSystem.rules.get(1), "northern", "Northern tamandua", null, List.of("anteater"));
        assertEquals("Zoo.fsh", codeSystem.rules.get(1).sourceInfo.file);
        assertConceptRule(codeSystem.rules.get(2), "southern", "Southern tamandua", null, List.of("anteater"));
        assertEquals("Zoo.fsh", codeSystem.rules.get(2).sourceInfo.file);
        assertConceptRule(codeSystem.rules.get(3), "northern", null, null, List.of("anteater"));
        assertEquals("Zoo.fsh", codeSystem.rules.get(3).sourceInfo.file);
        assertCaretValueRule(
          codeSystem.rules.get(4),
          "",
          "property[0].valueString",
          "They are strong climbers.",
          false,
          List.of("#anteater", "#northern")
        );
        assertEquals("Zoo.fsh", codeSystem.rules.get(4).sourceInfo.file);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should parse a rule set that uses an indented CaretValueRule on a top-level concept")
      void parseRuleSetUsesIndentedCaretValueRule() {
        var input = leftAlign("\n"
                                   + "      RuleSet: ZOO\n"
                                   + "      * #anteater \"Anteater\"\n"
                                   + "        * ^property[0].valueString = \"Their threat pose is really cute.\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        var codeSystem = result.ruleSets.get("ZOO");
        assertConceptRule(codeSystem.rules.get(0), "anteater", "Anteater", null, List.of());
        assertEquals("Zoo.fsh", codeSystem.rules.get(0).sourceInfo.file);
        assertCaretValueRule(
          codeSystem.rules.get(1),
          "#anteater",
          "property[0].valueString",
          "Their threat pose is really cute.",
          false,
          List.of("#anteater")
        );
        assertEquals("Zoo.fsh", codeSystem.rules.get(1).sourceInfo.file);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should parse a value set that uses an indented CaretValueRule after a ValueSetConceptComponentRule")
      void parseValueSetUsesIndentedCaretValueRule() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * ZOO#hippo \"Hippopotamus\"\n"
                                   + "        * ^designation.value = \"hipopótamo\"\n"
                                   + "        * ^designation.language = \"es\"\n"
                                   + "      * include ZOO#anteater \"Anteater\"\n"
                                   + "        * ^designation.value = \"fourmilier\"\n"
                                   + "        * ^designation.language = \"fr\"\n"
                                   + "      * exclude ZOO#bear \"Bear\"\n"
                                   + "        * ^designation.value = \"oso\"\n"
                                   + "        * ^designation.language = \"es\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(8, valueSet.rules.size());
        assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus").withLocation(new int[] {3, 3, 3, 26}).withFile("Zoo.fsh"),
          new FshCode("anteater", "ZOO", "Anteater").withLocation(new int[] {6, 11, 6, 33}).withFile("Zoo.fsh")));
        assertCaretValueRule(valueSet.rules.get(1), "", "designation.value", "hipopótamo", false, List.of("ZOO#hippo"));
        assertCaretValueRule(valueSet.rules.get(2), "", "designation.language", "es", false, List.of("ZOO#hippo"));
        assertCaretValueRule(valueSet.rules.get(3), "", "designation.value", "fourmilier", false, List.of("ZOO#anteater"));
        assertCaretValueRule(valueSet.rules.get(4), "", "designation.language", "fr", false, List.of("ZOO#anteater"));
        assertCaretValueRule(valueSet.rules.get(6), "", "designation.value", "oso", false, List.of("ZOO#bear"));
        assertCaretValueRule(valueSet.rules.get(7), "", "designation.language", "es", false, List.of("ZOO#bear"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should parse a value set that uses an indented CaretValueRule after a ValueSetConceptComponentRule with the from keyword")
      void parseValueSetUsesIndentedCaretValueRule2() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * #hippo \"Hippopotamus\" from system ZOO\n"
                                   + "        * ^designation.value = \"hipopótamo\"\n"
                                   + "        * ^designation.language = \"es\"\n"
                                   + "      * include #anteater \"Anteater\" from system ZOO\n"
                                   + "        * ^designation.value = \"fourmilier\"\n"
                                   + "        * ^designation.language = \"fr\"\n"
                                   + "      * exclude #bear \"Bear\" from system ZOO\n"
                                   + "        * ^designation.value = \"oso\"\n"
                                   + "        * ^designation.language = \"es\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(8, valueSet.rules.size());
        assertCaretValueRule(valueSet.rules.get(1), "", "designation.value", "hipopótamo", false, List.of("ZOO#hippo"));
        assertCaretValueRule(valueSet.rules.get(2), "", "designation.language", "es", false, List.of("ZOO#hippo"));
        assertCaretValueRule(valueSet.rules.get(3), "", "designation.value", "fourmilier", false, List.of("ZOO#anteater"));
        assertCaretValueRule(valueSet.rules.get(4), "", "designation.language", "fr", false, List.of("ZOO#anteater"));
        assertCaretValueRule(valueSet.rules.get(6), "", "designation.value", "oso", false, List.of("ZOO#bear"));
        assertCaretValueRule(valueSet.rules.get(7), "", "designation.language", "es", false, List.of("ZOO#bear"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should parse a value set that uses an indented InsertRule after a ValueSetConceptComponentRule")
      void parseValueSetUsesIndentedInsertRule() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * ZOO#hippo \"Hippopotamus\"\n"
                                   + "        * insert MyRuleSet\n"
                                   + "      * include ZOO#anteater \"Anteater\"\n"
                                   + "        * insert OtherRuleSet\n"
                                   + "      * exclude ZOO#bear \"Bear\"\n"
                                   + "        * insert ThirdRuleSet\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(5, valueSet.rules.size());
        assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus").withLocation(new int[] {3, 3, 3, 26}).withFile("Zoo.fsh"),
          new FshCode("anteater", "ZOO", "Anteater").withLocation(new int[] {5, 11, 5, 33}).withFile("Zoo.fsh")));
        assertInsertRule(valueSet.rules.get(1), "ZOO#hippo", "MyRuleSet", List.of(), List.of("ZOO#hippo"));
        assertInsertRule(valueSet.rules.get(2), "ZOO#anteater", "OtherRuleSet", List.of(), List.of("ZOO#anteater"));
        assertValueSetConceptComponent(
          valueSet.rules.get(3),
          "ZOO",
          null,
          List.of(new FshCode("bear", "ZOO", "Bear").withFile("Zoo.fsh").withLocation(new int[] {7, 11, 7, 25})),
          false
        );
        assertInsertRule(valueSet.rules.get(4), "ZOO#bear", "ThirdRuleSet", List.of(), List.of("ZOO#bear"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should parse a value set that uses a root-level CaretValueRule after a ValueSetConceptComponentRule")
      void parseValueSetUsesRootLevel() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * ZOO#hippo \"Hippopotamus\"\n"
                                   + "      * ^publisher = \"foo\"\n"
                                   + "      * include ZOO#anteater \"Anteater\"\n"
                                   + "      * ^immutable = false\n"
                                   + "      * exclude ZOO#bear \"Bear\"\n"
                                   + "      * ^purpose = \"We like animals.\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(5, valueSet.rules.size());
        assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus").withFile("Zoo.fsh").withLocation(new int[] {3, 3, 3, 26}),
          new FshCode("anteater", "ZOO", "Anteater").withLocation(new int[] {5, 11, 5, 33}).withFile("Zoo.fsh")));
        assertCaretValueRule(valueSet.rules.get(1), "", "publisher", "foo", false, List.of());
        assertCaretValueRule(valueSet.rules.get(2), "", "immutable", false, false, List.of());
        assertValueSetConceptComponent(
          valueSet.rules.get(3),
          "ZOO",
          null,
          List.of(new FshCode("bear", "ZOO", "Bear").withFile("Zoo.fsh").withLocation(new int[] {7, 11, 7, 25})),
          false
        );
        assertCaretValueRule(valueSet.rules.get(4), "", "purpose", "We like animals.", false, List.of());
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should parse a value set that uses a root-level CaretValueRule after a concept-level CaretValueRule")
      void parseValueSetUsesRootLevel2() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * ZOO#hippo \"Hippopotamus\"\n"
                                   + "      * ZOO#hippo ^designation.value = \"hipopótamo\"\n"
                                   + "      * ^publisher = \"foo\"\n"
                                   + "      * include ZOO#anteater \"Anteater\"\n"
                                   + "      * ZOO#anteater ^designation.value = \"fourmilier\"\n"
                                   + "      * ^immutable = false\n"
                                   + "      * exclude ZOO#bear \"Bear\"\n"
                                   + "        * ^designation.value = \"oso\"\n"
                                   + "      * ^purpose = \"We like animals.\"\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(8, valueSet.rules.size());
        assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus").withFile("Zoo.fsh").withLocation(new int[] {3, 3, 3, 26}),
          new FshCode("anteater", "ZOO", "Anteater").withFile("Zoo.fsh").withLocation(new int[] {6, 11, 6, 33})));
        assertCaretValueRule(valueSet.rules.get(1), "", "designation.value", "hipopótamo", false, List.of("ZOO#hippo"));
        assertCaretValueRule(valueSet.rules.get(2), "", "publisher", "foo", false, List.of());
        assertCaretValueRule(valueSet.rules.get(3), "", "designation.value", "fourmilier", false, List.of("ZOO#anteater"));
        assertCaretValueRule(valueSet.rules.get(4), "", "immutable", false, false, List.of());
        assertValueSetConceptComponent(
          valueSet.rules.get(5),
          "ZOO",
          null,
          List.of(new FshCode("bear", "ZOO", "Bear").withFile("Zoo.fsh").withLocation(new int[] {9, 11, 9, 25})),
          false
        );
        assertCaretValueRule(valueSet.rules.get(6), "", "designation.value", "oso", false, List.of("ZOO#bear"));
        assertCaretValueRule(valueSet.rules.get(7), "", "purpose", "We like animals.", false, List.of());
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter code context should log an error when parsing a value set that uses an indented CaretValueRule after a ValueSetFilterComponentRule")
      void logErrorParsingValueSetUses() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * include codes from system ZOO\n"
                                   + "        * ^inactive = true\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(2, valueSet.rules.size());
        assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
        assertCaretValueRule(valueSet.rules.get(1), "", "inactive", true, false);
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("Rule cannot be indented below rule which has no path.*File: Zoo\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter code context should log an error when parsing a value set that uses an indented InsertRule after a ValueSetFilterComponentRule")
      void logErrorParsingValueSetUses2() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * include codes from system ZOO\n"
                                   + "        * insert MyRuleSet\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(2, valueSet.rules.size());
        assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
        assertInsertRule(valueSet.rules.get(1), "", "MyRuleSet");
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("Rule cannot be indented below rule which has no path.*File: Zoo\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter code context should log an error when parsing a value set that uses an indented InsertRule with a concept after a ValueSetConceptComponentRule")
      void logErrorParsingValueSetUses3() {
        var input = leftAlign("\n"
                                   + "      ValueSet: ZooVS\n"
                                   + "      * ZOO#hippo \"Hippopotamus\"\n"
                                   + "        * ZOO#big-hippo insert MyRuleSet\n"
                                   + "      ");
        var result = importSingleText(input, "Zoo.fsh");
        assertEquals(1, result.valueSets.size());
        var valueSet = result.valueSets.get("ZooVS");
        assertEquals(1, valueSet.rules.size());
        assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus").withLocation(new int[] {3, 3, 3, 26}).withFile("Zoo.fsh")));
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("Only one concept may be listed before an insert rule on a ValueSet\\..*File: Zoo\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
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
