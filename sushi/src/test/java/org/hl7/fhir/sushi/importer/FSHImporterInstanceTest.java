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

// port of sushi/test/import/FSHImporter.Instance.test.ts (SUSHI 3.21.1)
class FSHImporterInstanceTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void beforeeach() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("Instance")
    class InstanceTests {
      @Nested
      @DisplayName("#instanceOf")
      class InstanceOfTests {
        @Test
        @DisplayName("FSHImporter Instance #instanceOf should parse the simplest possible instance")
        void parseSimplestPossibleInstance() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        ";
          var result = importSingleText(input, "SimpleInstance.fsh");
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyObservation");
          assertEquals("MyObservation", instance.name);
          assertEquals("Observation", instance.instanceOf);
          assertNull(instance.title);
          assertNull(instance.description);
          assertEquals(0, instance.rules.size());
          assertToEqual(new TextLocation(2, 9, 3, 31), instance.sourceInfo.location);
          assertEquals("SimpleInstance.fsh", instance.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter Instance #instanceOf should parse numeric instance name and numeric instanceOf")
        void parseNumericInstanceNameNumericInstanceOf() {
          // NOT recommended, but possible
          var input = "\n"
                           + "        Instance: 123\n"
                           + "        InstanceOf: 456\n"
                           + "        ";
          var result = importSingleText(input, "SimpleInstance.fsh");
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("123");
          assertEquals("123", instance.name);
          assertEquals("456", instance.instanceOf);
        }

        @Test
        @DisplayName("FSHImporter Instance #instanceOf should parse an instance with an aliased type")
        void parseInstanceAliasedType() {
          var input = "\n"
                           + "        Alias: obs = Observation\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: obs\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyObservation");
          assertEquals("Observation", instance.instanceOf);
        }

        @Test
        @DisplayName("FSHImporter Instance #instanceOf should not parse an instance that has no type")
        void notParseInstanceHasNoType() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        Title: \"My Important Observation\"\n"
                           + "        ";
          var result = importSingleText(input, "Missing.fsh");
          assertEquals(0, result.instances.size());
          assertTrue(matches(Pattern.compile("File: Missing\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#title")
      class TitleTests {
        @Test
        @DisplayName("FSHImporter Instance #title should parse an instance with a title")
        void parseInstanceTitle() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Title: \"My Important Observation\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyObservation");
          assertEquals("MyObservation", instance.name);
          assertEquals("Observation", instance.instanceOf);
          assertEquals("My Important Observation", instance.title);
        }

      }

      @Nested
      @DisplayName("#description")
      class DescriptionTests {
        @Test
        @DisplayName("FSHImporter Instance #description should parse an instance with a description")
        void parseInstanceDescription() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Description: \"Shows an example of an Observation\"\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyObservation");
          assertEquals("MyObservation", instance.name);
          assertEquals("Observation", instance.instanceOf);
          assertEquals("Shows an example of an Observation", instance.description);
        }

      }

      @Nested
      @DisplayName("#usage")
      class UsageTests {
        @Test
        @DisplayName("FSHImporter Instance #usage should parse an instance with a usage")
        void parseInstanceUsage() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Usage: #example\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyObservation");
          assertEquals("MyObservation", instance.name);
          assertEquals("Observation", instance.instanceOf);
          assertEquals(InstanceUsage.Example, instance.usage);
        }

        @Test
        @DisplayName("FSHImporter Instance #usage should log an error for invalid usage and set default usage to example")
        void logErrorForInvalidUsageSet() {
          var input = "\n"
                           + "        Instance: MyBadObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Usage: #baduse\n"
                           + "        ";
          var result = importSingleText(input, "Bad.fsh");
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyBadObservation");
          assertEquals("MyBadObservation", instance.name);
          assertEquals("Observation", instance.instanceOf);
          assertEquals(InstanceUsage.Example, instance.usage);
          assertTrue(matches(Pattern.compile("Invalid Usage. Supported usage codes are \"#example\", \"#definition\", and \"#inline\". Instance will be treated as an example.", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Bad\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Instance #usage should log a warning if a system is specified on usage")
        void logWarningIfSystemSpecifiedUsage() {
          var input = "\n"
                           + "        Instance: MyBadObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Usage: badsystem#example\n"
                           + "        ";
          var result = importSingleText(input, "Bad.fsh");
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyBadObservation");
          assertEquals("MyBadObservation", instance.name);
          assertEquals("Observation", instance.instanceOf);
          assertEquals(InstanceUsage.Example, instance.usage);
          assertTrue(matches(Pattern.compile("Do not specify a system for instance Usage.", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
          assertTrue(matches(Pattern.compile("File: Bad\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
        }

        @Test
        @DisplayName("FSHImporter Instance #usage should log a warning if a conformance or terminology resource does not have a usage (and therefore will default to example)")
        void logWarningIfConformanceOrTerminology() {
          // No usage specified for a CapabilityStatement instance
          var input = "\n"
                           + "        Instance: MyCapabilityStatementDefinition\n"
                           + "        InstanceOf: CapabilityStatement\n"
                           + "        ";
          var result = importSingleText(input, "CS.fsh");
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyCapabilityStatementDefinition");
          assertEquals("MyCapabilityStatementDefinition", instance.name);
          assertEquals("CapabilityStatement", instance.instanceOf);
          assertEquals(InstanceUsage.Example, instance.usage); // default is still used but warning is logged
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, loggerSpy.getAllMessages("warn").size());
          assertTrue(matches(Pattern.compile("No usage was specified on MyCapabilityStatementDefinition\\. The default #example usage will be applied, but CapabilityStatement Instances are typically definitions", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
          assertTrue(matches(Pattern.compile("File: CS\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
        }

      }

      @Nested
      @DisplayName("#mixins")
      class MixinsTests {
        @Test
        @DisplayName("FSHImporter Instance #mixins should log an error when the deprecated Mixins keyword is used")
        void logErrorDeprecatedMixinsKeywordUsed() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Mixins: RuleSet1 and RuleSet2\n"
                           + "        ";
          var result = importSingleText(input, "Deprecated.fsh");
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyObservation");
          assertEquals("MyObservation", instance.name);
          assertTrue(matches(Pattern.compile("The 'Mixins' keyword is no longer supported\\.", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Deprecated\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#assignmentRule")
      class AssignmentRuleTests {
        @Test
        @DisplayName("FSHImporter Instance #assignmentRule should parse an instance with assigned value rules")
        void parseInstanceAssignedValueRules() {
          var input = "\n"
                           + "        Instance: SamplePatient\n"
                           + "        InstanceOf: Patient\n"
                           + "        Title: \"Georgio Manos\"\n"
                           + "        Description: \"An example of a fictional patient named Georgio Manos\"\n"
                           + "        Usage: #example\n"
                           + "        * name[0].family = \"Georgio\"\n"
                           + "        * name[0].given[0] = \"Manos\"\n"
                           + "        * gender = #other\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("SamplePatient");
          assertEquals("Patient", instance.instanceOf);
          assertEquals("Georgio Manos", instance.title);
          assertEquals("An example of a fictional patient named Georgio Manos", instance.description);
          assertEquals(InstanceUsage.Example, instance.usage);
          assertEquals(3, instance.rules.size());
          assertAssignmentRule(instance.rules.get(0), "name[0].family", "Georgio");
          assertAssignmentRule(instance.rules.get(1), "name[0].given[0]", "Manos");
          assertAssignmentRule(
            instance.rules.get(2),
            "gender",
            new FshCode("other").withLocation(new int[] {9, 20, 9, 25}).withFile("")
          );
        }

        @Test
        @DisplayName("FSHImporter Instance #assignmentRule should parse an instance with assigned values that are an alias")
        void parseInstanceAssignedValuesAreAlias() {
          var input = "\n"
                           + "        Alias: EXAMPLE = http://example.org\n"
                           + "\n"
                           + "        Instance: PatientExample\n"
                           + "        InstanceOf: Patient\n"
                           + "        * identifier[0].system = EXAMPLE\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("PatientExample");
          assertEquals(1, instance.rules.size());
          assertEquals("Patient", instance.instanceOf);
          assertAssignmentRule(instance.rules.get(0), "identifier[0].system", "http://example.org");
        }

        @Test
        @DisplayName("FSHImporter Instance #assignmentRule should parse an instance with assigned value resource rules")
        void parseInstanceAssignedValueResourceRules() {
          var input = "\n"
                           + "        Instance: SamplePatient\n"
                           + "        InstanceOf: Patient\n"
                           + "        Title: \"Georgio Manos\"\n"
                           + "        Description: \"An example of a fictional patient named Georgio Manos\"\n"
                           + "        * contained[0] = SomeInstance\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("SamplePatient");
          assertEquals("Patient", instance.instanceOf);
          assertEquals("Georgio Manos", instance.title);
          assertEquals("An example of a fictional patient named Georgio Manos", instance.description);
          assertEquals(1, instance.rules.size());
          assertAssignmentRule(instance.rules.get(0), "contained[0]", "SomeInstance", false, true);
        }

      }

      @Nested
      @DisplayName("#pathRule")
      class PathRuleTests {
        @Test
        @DisplayName("FSHImporter Instance #pathRule should parse a pathRule")
        void parsePathRule() {
          var input = "\n"
                           + "        Instance: PatientProfile\n"
                           + "        InstanceOf: Patient\n"
                           + "        * name\n"
                           + "        ";
          var result = importSingleText(input, "Path.fsh");
          var instance = result.instances.get("PatientProfile");
          assertEquals(1, instance.rules.size());
          assertPathRule(instance.rules.get(0), "name");
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter Instance #insertRule should parse an insert rule with a single RuleSet")
        void parseInsertRuleSingleRuleSet() {
          var input = "\n"
                           + "        Instance: MyPatient\n"
                           + "        InstanceOf: Patient\n"
                           + "        * insert MyRuleSet\n"
                           + "        ";
          var result = importSingleText(input, "Insert.fsh");
          var instance = result.instances.get("MyPatient");
          assertEquals(1, instance.rules.size());
          assertInsertRule(instance.rules.get(0), "", "MyRuleSet");
        }

        @Test
        @DisplayName("FSHImporter Instance #insertRule should parse an insert rule with an empty parameter value")
        void parseInsertRuleEmptyParameterValue() {
          // Example taken from open-hie/case-reporting, which failed a regression in this area
          var input = "\n"
                           + "        RuleSet: Question(context, linkId, text, type, repeats)\n"
                           + "        * {context}item[+].linkId = \"{linkId}\"\n"
                           + "        * {context}item[=].text = \"{text}\"\n"
                           + "        * {context}item[=].type = #{type}\n"
                           + "        * {context}item[=].repeats = {repeats}\n"
                           + "\n"
                           + "        Instance: case-reporting-questionnaire\n"
                           + "        InstanceOf: Questionnaire\n"
                           + "        * insert Question(,title, HIV Case Report, display, false)\n"
                           + "        ";
          var result = importSingleText(input, "Insert.fsh");
          var instance = result.instances.get("case-reporting-questionnaire");
          assertEquals(1, instance.rules.size());
          assertInsertRule(instance.rules.get(0), "", "Question", List.of("",
            "title",
            "HIV Case Report",
            "display",
            "false"));
        }

      }

      @Nested
      @DisplayName("#instanceMetadata")
      class InstanceMetadataTests {
        @Test
        @DisplayName("FSHImporter Instance #instanceMetadata should only apply each metadata attribute the first time it is declared")
        void onlyApplyEachMetadataAttributeFirst() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Title: \"My Important Observation\"\n"
                           + "        Description: \"My Observation Description\"\n"
                           + "        Usage: #example\n"
                           + "        InstanceOf: DuplicateObservation\n"
                           + "        Title: \"My Duplicate Observation\"\n"
                           + "        Description: \"My Duplicate Observation Description\"\n"
                           + "        Usage: #non-example\n"
                           + "        ";
          var result = importSingleText(input);
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyObservation");
          assertEquals("MyObservation", instance.name);
          assertEquals("Observation", instance.instanceOf);
          assertEquals("My Important Observation", instance.title);
          assertEquals("My Observation Description", instance.description);
          assertEquals(InstanceUsage.Example, instance.usage);
        }

        @Test
        @DisplayName("FSHImporter Instance #instanceMetadata should log an error when encountering a duplicate metadata attribute")
        void logErrorEncounteringDuplicateMetadataAttribute() {
          var input = "\n"
                           + "        Instance: MyObservation\n"
                           + "        InstanceOf: Observation\n"
                           + "        Title: \"My Important Observation\"\n"
                           + "        Description: \"My Observation Description\"\n"
                           + "        Usage: #example\n"
                           + "        InstanceOf: DuplicateObservation\n"
                           + "        Title: \"My Duplicate Observation\"\n"
                           + "        Description: \"My Duplicate Observation Description\"\n"
                           + "        Usage: #non-example\n"
                           + "        ";
          importSingleText(input, "Dupe.fsh");
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 7\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-4, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 8\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 9\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 10\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Instance #instanceMetadata should log an error and skip the instance when encountering an instance with a name used by another instance")
        void logErrorSkipInstanceEncounteringInstance() {
          var input = "\n"
                           + "        Instance: MyInstance\n"
                           + "        InstanceOf: Observation\n"
                           + "\n"
                           + "        Instance: MyInstance\n"
                           + "        InstanceOf: Patient\n"
                           + "        ";
          var result = importSingleText(input, "SameName.fsh");
          assertEquals(1, result.instances.size());
          var instance = result.instances.get("MyInstance");
          assertEquals("Observation", instance.instanceOf);
          assertTrue(matches(Pattern.compile("Instance named MyInstance already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 5 - 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Instance #instanceMetadata should log an error and skip the instance when encountering an instance with a name used by another instance in another file")
        void logErrorSkipInstanceEncounteringInstance2() {
          var input1 = "\n"
                            + "          Instance: SomeInstance\n"
                            + "          InstanceOf: Patient\n"
                            + "          Title: \"Instance 1\"\n"
                            + "        ";
          var input2 = "\n"
                            + "          Instance: SomeInstance\n"
                            + "          InstanceOf: Patient\n"
                            + "          Title: \"Instance 2\"\n"
                            + "        ";
          var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"),
            new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.instances.size()).sum());
          var instance = result.get(0).instances.get("SomeInstance");
          assertEquals("Instance 1", instance.title);
          assertTrue(matches(Pattern.compile("Instance named SomeInstance already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
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
