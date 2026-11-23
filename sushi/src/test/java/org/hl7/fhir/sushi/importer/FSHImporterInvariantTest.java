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

// port of sushi/test/import/FSHImporter.Invariant.test.ts (SUSHI 3.21.1)
class FSHImporterInvariantTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @Nested
    @DisplayName("Invariant")
    class InvariantTests {
      @BeforeEach void beforeeach() {
        loggerSpy.reset();
      }

      @Nested
      @DisplayName("#invariantMetadata")
      class InvariantMetadataTests {
        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should parse the simplest possible invariant")
        void parseSimplestPossibleInvariant() {
          var input = "\n"
                           + "        Invariant: emp-1\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This does not actually require anything.\"\n"
                           + "        ";
          var result = importSingleText(input, "Empty.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("emp-1");
          assertEquals("emp-1", invariant.name);
          FshCode severityCode = new FshCode("error")
            .withLocation(new int[] {3, 19, 3, 24})
            .withFile("Empty.fsh");
          assertToEqual(severityCode, invariant.severity);
          assertEquals("This does not actually require anything.", invariant.description);
          assertToEqual(new TextLocation(2, 9, 4, 63), invariant.sourceInfo.location);
          assertEquals("Empty.fsh", invariant.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should parse numeric invariant name")
        void parseNumericInvariantName() {
          // NOT recommended, but possible
          var input = "\n"
                           + "        Invariant: 123\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This does not actually require anything.\"\n"
                           + "        ";
          var result = importSingleText(input, "Empty.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("123");
          assertEquals("123", invariant.name);
        }

        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should parse an invariant with additional metadata")
        void parseInvariantAdditionalMetadata() {
          var input = "\n"
                           + "        Invariant: full-1\n"
                           + "        Description: \"This resource must define a cage and aquarium.\"\n"
                           + "        Expression: \"cage.exists() and aquarium.exists()\"\n"
                           + "        XPath: \"exists(f:cage) and exists(f:aquarium)\"\n"
                           + "        Severity: #error\n"
                           + "        ";
          var result = importSingleText(input, "Full.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("full-1");
          assertEquals("full-1", invariant.name);
          assertEquals("This resource must define a cage and aquarium.", invariant.description);
          assertEquals("cage.exists() and aquarium.exists()", invariant.expression);
          assertEquals("exists(f:cage) and exists(f:aquarium)", invariant.xpath);
          FshCode severityCode = new FshCode("error").withLocation(new int[] {6, 19, 6, 24}).withFile("Full.fsh");
          assertToEqual(severityCode, invariant.severity);
          assertToEqual(new TextLocation(2, 9, 6, 24), invariant.sourceInfo.location);
          assertEquals("Full.fsh", invariant.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should parse an invariant with multiline expression")
        void parseInvariantMultilineExpression() {
          var input = "\n"
                           + "        Invariant: multiline-1\n"
                           + "        Description: \"This invariant has a multiline expression.\"\n"
                           + "        Expression: \"\"\"\n"
                           + "          (cage.exists() and aquarium.exists()) or\n"
                           + "          (habitat.exists() and enclosure.exists())\n"
                           + "        \"\"\"\n"
                           + "        Severity: #error\n"
                           + "        ";
          var result = importSingleText(input, "Multiline.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("multiline-1");
          assertEquals("multiline-1", invariant.name);
          assertEquals("This invariant has a multiline expression.", invariant.description);
          assertEquals("(cage.exists() and aquarium.exists()) or\n(habitat.exists() and enclosure.exists())", invariant.expression);
          FshCode severityCode = new FshCode("error")
            .withLocation(new int[] {8, 19, 8, 24})
            .withFile("Multiline.fsh");
          assertToEqual(severityCode, invariant.severity);
          assertToEqual(new TextLocation(2, 9, 8, 24), invariant.sourceInfo.location);
          assertEquals("Multiline.fsh", invariant.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should only apply each metadata attribute the first time it is declared")
        void onlyApplyEachMetadataAttributeFirst() {
          var input = "\n"
                           + "        Invariant: twice-1\n"
                           + "        Description: \"This resource is described.\"\n"
                           + "        Expression: \"description.exists()\"\n"
                           + "        XPath: \"exists(f:description)\"\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This resource is not described.\"\n"
                           + "        Expression: \"not(description.exists())\"\n"
                           + "        XPath: \"not(exists(f:description))\"\n"
                           + "        Severity: #warning\n"
                           + "        ";
          var result = importSingleText(input, "Twice.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("twice-1");
          assertEquals("twice-1", invariant.name);
          assertEquals("This resource is described.", invariant.description);
          assertEquals("description.exists()", invariant.expression);
          assertEquals("exists(f:description)", invariant.xpath);
          FshCode severityCode = new FshCode("error")
            .withLocation(new int[] {6, 19, 6, 24})
            .withFile("Twice.fsh");
          assertToEqual(severityCode, invariant.severity);
        }

        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should log an error when encountering a duplicate metadata attribute")
        void logErrorEncounteringDuplicateMetadataAttribute() {
          var input = "\n"
                           + "        Invariant: twice-1\n"
                           + "        Description: \"This resource is described.\"\n"
                           + "        Expression: \"description.exists()\"\n"
                           + "        XPath: \"exists(f:description)\"\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This resource is not described.\"\n"
                           + "        Expression: \"not(description.exists())\"\n"
                           + "        XPath: \"not(exists(f:description))\"\n"
                           + "        Severity: #warning\n"
                           + "        ";
          importSingleText(input, "Twice.fsh");
          assertTrue(matches(Pattern.compile("File: Twice\\.fsh.*Line: 7\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-4, "error")));
          assertTrue(matches(Pattern.compile("File: Twice\\.fsh.*Line: 8\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Twice\\.fsh.*Line: 9\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Twice\\.fsh.*Line: 10\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should log an error and skip the invariant when encountering a invariant with a name used by another invariant")
        void logErrorSkipInvariantEncounteringInvariant() {
          var input = "\n"
                           + "        Invariant: same-1\n"
                           + "        Severity: #error\n"
                           + "        Description: \"First description.\"\n"
                           + "\n"
                           + "        Invariant: same-1\n"
                           + "        Severity: #error\n"
                           + "        Description: \"Second description.\"\n"
                           + "        ";
          var result = importSingleText(input, "SameName.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("same-1");
          assertEquals("First description.", invariant.description);
          assertTrue(matches(Pattern.compile("Invariant named same-1 already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 6 - 8\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter Invariant #invariantMetadata should log an error and skip the invariant when encountering an invariant with a name used by another invariant in another file")
        void logErrorSkipInvariantEncounteringInvariant2() {
          var input1 = "\n"
                            + "          Invariant: same-1\n"
                            + "          Severity: #error\n"
                            + "          Description: \"First description.\"\n"
                            + "        ";
          var input2 = "\n"
                            + "          Invariant: same-1\n"
                            + "          Severity: #error\n"
                            + "          Description: \"Second description.\"\n"
                            + "        ";
          var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"),
            new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.invariants.size()).sum());
          var invariant = result.get(0).invariants.get("same-1");
          assertEquals("First description.", invariant.description);
          assertTrue(matches(Pattern.compile("Invariant named same-1 already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 4\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#assignmentRule")
      class AssignmentRuleTests {
        @Test
        @DisplayName("FSHImporter Invariant #assignmentRule should parse an invariant with assigned value rules")
        void parseInvariantAssignedValueRules() {
          var input = "\n"
                           + "        Invariant: rules-1\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This has some rules.\"\n"
                           + "        * requirements = \"This invariant exists because I willed it so.\"\n"
                           + "        * expression = \"name.exists()\"\n"
                           + "        ";
          var result = importSingleText(input, "InvariantRules.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("rules-1");
          assertEquals("rules-1", invariant.name);
          FshCode severityCode = new FshCode("error")
            .withLocation(new int[] {3, 19, 3, 24})
            .withFile("InvariantRules.fsh");
          assertToEqual(severityCode, invariant.severity);
          assertEquals("This has some rules.", invariant.description);
          assertEquals(2, invariant.rules.size());
          assertAssignmentRule(
            invariant.rules.get(0),
            "requirements",
            "This invariant exists because I willed it so."
          );
          assertAssignmentRule(invariant.rules.get(1), "expression", "name.exists()");
        }

        @Test
        @DisplayName("FSHImporter Invariant #assignmentRule should parse an instance with assigned values that are an alias")
        void parseInstanceAssignedValuesAreAlias() {
          var input = "\n"
                           + "        Alias: SOURCE = http://example.org/something\n"
                           + "\n"
                           + "        Invariant: rules-2\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This has a rule.\"\n"
                           + "        * source = SOURCE\n"
                           + "        ";
          var result = importSingleText(input, "InvariantRules.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("rules-2");
          assertEquals(1, invariant.rules.size());
          assertAssignmentRule(invariant.rules.get(0), "source", "http://example.org/something");
        }

      }

      @Nested
      @DisplayName("#pathRule")
      class PathRuleTests {
        @Test
        @DisplayName("FSHImporter Invariant #pathRule should parse a pathRule but then discard it")
        void parsePathRuleButThenDiscard() {
          var input = "\n"
                           + "        Invariant: rules-3\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This has a rule.\"\n"
                           + "        * requirements\n"
                           + "        ";
          var result = importSingleText(input, "InvariantRules.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("rules-3");
          assertEquals(0, invariant.rules.size());
        }

        @Test
        @DisplayName("FSHImporter Invariant #pathRule should use a pathRule to construct a full path")
        void usePathRuleConstructFullPath() {
          var input = leftAlign("\n"
                                     + "        Invariant: rules-3\n"
                                     + "        Severity: #error\n"
                                     + "        Description: \"This has a rule.\"\n"
                                     + "        * requirements\n"
                                     + "          * id = \"req-id\"\n"
                                     + "        ");
          var result = importSingleText(input, "InvariantRules.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("rules-3");
          assertEquals(1, invariant.rules.size());
          assertAssignmentRule(invariant.rules.get(0), "requirements.id", "req-id");
        }

        @Test
        @DisplayName("FSHImporter Invariant #pathRule should properly handle soft indices with pathRules")
        void properlyHandleSoftIndicesPathRules() {
          var input = leftAlign("\n"
                                     + "        Invariant: rules-3\n"
                                     + "        Severity: #error\n"
                                     + "        Description: \"This has a rule.\"\n"
                                     + "        * extension[+]\n"
                                     + "          * url = \"http://example.org/ext1\"\n"
                                     + "          * valueBoolean = true\n"
                                     + "        * extension[+]\n"
                                     + "          * url = \"http://example.org/ext2\"\n"
                                     + "          * valueBoolean = false\n"
                                     + "        ");
          var result = importSingleText(input, "InvariantRules.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("rules-3");
          assertEquals(4, invariant.rules.size());
          assertAssignmentRule(invariant.rules.get(0), "extension[+].url", "http://example.org/ext1");
          assertAssignmentRule(invariant.rules.get(1), "extension[=].valueBoolean", true);
          assertAssignmentRule(invariant.rules.get(2), "extension[+].url", "http://example.org/ext2");
          assertAssignmentRule(invariant.rules.get(3), "extension[=].valueBoolean", false);
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter Invariant #insertRule should parse an insert rule")
        void parseInsertRule() {
          var input = "\n"
                           + "        Invariant: rules-4\n"
                           + "        Severity: #error\n"
                           + "        Description: \"This has a rule.\"\n"
                           + "        * insert MyRuleSet\n"
                           + "        ";
          var result = importSingleText(input, "InvariantRules.fsh");
          assertEquals(1, result.invariants.size());
          var invariant = result.invariants.get("rules-4");
          assertEquals(1, invariant.rules.size());
          assertInsertRule(invariant.rules.get(0), "", "MyRuleSet");
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
