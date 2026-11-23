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

// port of sushi/test/import/FSHImporter.Alias.test.ts (SUSHI 3.21.1)
class FSHImporterAliasTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void beforeeach() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("Alias")
    class AliasTests {
      @Test
      @DisplayName("FSHImporter Alias should collect and return aliases in result")
      void collectReturnAliasesResult() {
        var input = leftAlign("\n"
                                   + "      Alias: LOINC = http://loinc.org\n"
                                   + "      Alias: SCT = http://snomed.info/sct\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "\n"
                                   + "      Alias: RXNORM = http://www.nlm.nih.gov/research/umls/rxnorm\n"
                                   + "\n"
                                   + "      Profile: AnotherObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "\n"
                                   + "      Alias: UCUM = http://unitsofmeasure.org\n"
                                   + "      ");
        var result = importSingleText(input);
        assertEquals(4, result.aliases.size());
        assertEquals("http://loinc.org", result.aliases.get("LOINC"));
        assertEquals("http://snomed.info/sct", result.aliases.get("SCT"));
        assertEquals("http://www.nlm.nih.gov/research/umls/rxnorm", result.aliases.get("RXNORM"));
        assertEquals("http://unitsofmeasure.org", result.aliases.get("UCUM"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should parse aliases that replicate the syntax of a code")
      void parseAliasesReplicateSyntaxCode() {
        var input = leftAlign("\n"
                                   + "      Alias: LOINC = http://loinc.org#1234\n"
                                   + "      ");
        var result = importSingleText(input);
        assertEquals(1, result.aliases.size());
        assertEquals("http://loinc.org#1234", result.aliases.get("LOINC"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should report when the same alias is defined twice w/ different values in the same file")
      void reportSameAliasDefinedTwiceW() {
        var input = leftAlign("\n"
                                   + "      Alias: USCoreRace = http://hl7.org/fhir/us/core/ValueSet/omb-race-category\n"
                                   + "      Alias: USCoreRace = http://hl7.org/fhir/us/core/StructureDefinition/us-core-race\n"
                                   + "      ");
        var result = importSingleText(input);
        assertEquals(1, result.aliases.size());
        assertEquals("http://hl7.org/fhir/us/core/ValueSet/omb-race-category", result.aliases.get("USCoreRace"));
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("Alias USCoreRace cannot be redefined to http:\\/\\/hl7.org\\/fhir\\/us\\/core\\/StructureDefinition\\/us-core-race; it is already defined as http:\\/\\/hl7.org\\/fhir\\/us\\/core\\/ValueSet\\/omb-race-category\\..*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should report when the same alias is defined twice w/ different values in different files")
      void reportSameAliasDefinedTwiceW2() {
        var input = leftAlign("\n"
                                   + "      Alias: USCoreRace = http://hl7.org/fhir/us/core/ValueSet/omb-race-category\n"
                                   + "      ");
        var input2 = leftAlign("\n"
                                    + "      Alias: USCoreRace = http://hl7.org/fhir/us/core/StructureDefinition/us-core-race\n"
                                    + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Alias1.fsh"),
          new RawFSH(input2, "Alias2.fsh")));
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).aliases.size());
        assertEquals("http://hl7.org/fhir/us/core/ValueSet/omb-race-category", results.get(0).aliases.get("USCoreRace"));
        assertEquals(0, results.get(1).aliases.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("Alias USCoreRace cannot be redefined to http:\\/\\/hl7.org\\/fhir\\/us\\/core\\/StructureDefinition\\/us-core-race; it is already defined as http:\\/\\/hl7.org\\/fhir\\/us\\/core\\/ValueSet\\/omb-race-category\\..*File: Alias2\\.fsh.*Line: 2\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should not report an error when the same alias is defined multiple times w/ the same values")
      void notReportErrorSameAliasDefined() {
        var input = leftAlign("\n"
                                   + "      Alias: USCoreRace = http://hl7.org/fhir/us/core/StructureDefinition/us-core-race\n"
                                   + "      Alias: USCoreRace = http://hl7.org/fhir/us/core/StructureDefinition/us-core-race\n"
                                   + "      ");
        var input2 = leftAlign("\n"
                                    + "      Alias: USCoreRace = http://hl7.org/fhir/us/core/StructureDefinition/us-core-race\n"
                                    + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Alias1.fsh"),
          new RawFSH(input2, "Alias2.fsh")));
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).aliases.size());
        assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race", results.get(0).aliases.get("USCoreRace"));
        assertEquals(1, results.get(1).aliases.size());
        assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race", results.get(1).aliases.get("USCoreRace"));
        assertEquals(0, loggerSpy.getAllLogs("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should translate an alias when the alias is defined before its use")
      void translateAliasAliasDefinedBeforeIts() {
        var input = leftAlign("\n"
                                   + "      Alias: LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code from LOINC\n"
                                   + "      ");
        var result = importSingleText(input);
        BindingRule rule = (BindingRule) result.profiles.get("ObservationProfile").rules.get(0);
        assertEquals("http://loinc.org", rule.valueSet);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should translate an alias when the alias is defined after its use")
      void translateAliasAliasDefinedAfterIts() {
        var input = leftAlign("\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code from LOINC\n"
                                   + "\n"
                                   + "      Alias: LOINC = http://loinc.org\n"
                                   + "      ");
        var result = importSingleText(input);
        BindingRule rule = (BindingRule) result.profiles.get("ObservationProfile").rules.get(0);
        assertEquals("http://loinc.org", rule.valueSet);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should not translate an alias when the alias does not match")
      void notTranslateAliasAliasDoesNot() {
        var input = leftAlign("\n"
                                   + "      Alias: LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code from LAINC\n"
                                   + "      ");
        var result = importSingleText(input);
        BindingRule rule = (BindingRule) result.profiles.get("ObservationProfile").rules.get(0);
        assertEquals("LAINC", rule.valueSet);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should translate an alias from any input file")
      void translateAliasFromAnyInputFile() {
        var input = leftAlign("\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code from LOINC\n"
                                   + "      ");
        var input2 = leftAlign("\n"
                                    + "      Alias: LOINC = http://loinc.org\n"
                                    + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input), new RawFSH(input2)));
        assertEquals(2, results.size());
        BindingRule rule = (BindingRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        assertEquals("http://loinc.org", rule.valueSet);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an aliased code prefixed with $ does not resolve")
      void logErrorAliasedCodePrefixedDoes() {
        var input = leftAlign("\n"
                                   + "      Alias: $LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = $LOINCZ#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Loinc.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$LOINCZ.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: Loinc.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an aliased value set rule prefixed with $ does not resolve")
      void logErrorAliasedValueSetRule() {
        var input = leftAlign("\n"
                                   + "      Alias: $LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code from $LOINCZ\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Loinc.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$LOINCZ.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: Loinc.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an aliased reference prefixed with $ does not resolve")
      void logErrorAliasedReferencePrefixedDoes() {
        var input = leftAlign("\n"
                                   + "      Alias: $MYPATIENT = http://hl7.org/fhir/StructureDefinition/mypatient.html\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * subject only Reference($MYPATIENTZ)\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "MyPatient.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$MYPATIENT.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: MyPatient.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an assignment rule aliased reference prefixed with $ does not resolve")
      void logErrorAssignmentRuleAliasedReference() {
        var input = leftAlign("\n"
                                   + "      Alias: $MYPATIENT = http://hl7.org/fhir/StructureDefinition/mypatient.html\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * subject = Reference($MYPATIENTZ)\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "MyPatient.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$MYPATIENTZ.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: MyPatient.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an only rule aliased reference prefixed with $ does not resolve")
      void logErrorOnlyRuleAliasedReference() {
        var input = leftAlign("\n"
                                   + "      Alias: $MYPATIENT = http://hl7.org/fhir/StructureDefinition/mypatient.html\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * subject only Reference($MYPATIENTZ)\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "MyPatient.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$MYPATIENTZ.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: MyPatient.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should not log an error when a contains rule aliased extension prefixed with $ does not resolve")
      void notLogErrorContainsRuleAliased() {
        loggerSpy.reset();
        var input = leftAlign("\n"
                                   + "      Alias: $MYEXTENSION = http://hl7.org/fhir/StructureDefinition/mypatient-extension.html\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * extension contains $TYPO 1..1\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "MyExtension.fsh")));
        assertEquals(1, results.size());
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an aliased contains rule type prefixed with $ does not resolve")
      void logErrorAliasedContainsRuleType() {
        var input = leftAlign("\n"
                                   + "      Alias: $EXT = http://fhir.org/extension\n"
                                   + "\n"
                                   + "      Profile: MyObservation\n"
                                   + "      Parent: Observation\n"
                                   + "      * extension contains $EXTZ named ext 1..1\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Ext.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$EXTZ.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: Ext.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an aliased value set system prefixed with $ does not resolve")
      void logErrorAliasedValueSetSystem() {
        var input = leftAlign("\n"
                                   + "      Alias: $LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      ValueSet: MySet\n"
                                   + "      * codes from system $LOINCZ\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Loinc.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$LOINCZ.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: Loinc.fsh.*Line: 5\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an aliased value set prefixed with $ does not resolve")
      void logErrorAliasedValueSetPrefixed() {
        var input = leftAlign("\n"
                                   + "      Alias: $LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      ValueSet: MySet\n"
                                   + "      * codes from valueset $LOINCZ\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Loinc.fsh")));
        assertEquals(1, results.size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("\\$LOINCZ.*\\$"), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: Loinc.fsh.*Line: 5\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should resolve an alias while accounting for a version")
      void resolveAliasWhileAccountingForVersion() {
        var input = leftAlign("\n"
                                   + "      Alias: LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = LOINC|123#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Loinc.fsh")));
        assertEquals(1, results.size());
        AssignmentRule rule = (AssignmentRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        assertToEqual(new FshCode("foo", "http://loinc.org|123")
            .withFile("Loinc.fsh")
            .withLocation(new int[] {6, 10, 6, 22}), rule.value);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should resolve an alias while ignoring an empty version")
      void resolveAliasWhileIgnoringEmptyVersion() {
        var input = leftAlign("\n"
                                   + "      Alias: LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = LOINC|#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Loinc.fsh")));
        assertEquals(1, results.size());
        AssignmentRule rule = (AssignmentRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        assertToEqual(new FshCode("foo", "http://loinc.org").withFile("Loinc.fsh").withLocation(new int[] {6, 10, 6, 19}), rule.value);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should log an error when an alias contains reserved characters")
      void logErrorAliasContainsReservedCharacters() {
        var input = leftAlign("\n"
                                   + "      Alias: BAD|LOINC = http://loinc.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = BAD|LOINC#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Loinc.fsh")));
        assertEquals(1, results.size());
        AssignmentRule rule = (AssignmentRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        // The BAD|LOINC alias does not resolve, since it is not created
        assertToEqual(new FshCode("foo", "BAD|LOINC").withFile("Loinc.fsh").withLocation(new int[] {6, 10, 6, 22}), rule.value);
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("BAD\\|LOINC cannot include \"\\|\""), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Alias should resolve an alias with all supported characters")
      void resolveAliasAllSupportedCharacters() {
        // The only supported characters in Aliases are ASCII letters, numbers, _, -, .
        var input = leftAlign("\n"
                                   + "      Alias: Foo_McBar-Baz_Jr.3 = http://example.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = Foo_McBar-Baz_Jr.3#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Alias.fsh")));
        assertEquals(1, results.size());
        AssignmentRule rule = (AssignmentRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        assertToEqual(new FshCode("foo", "http://example.org").withFile("Alias.fsh").withLocation(new int[] {6, 10, 6, 31}), rule.value);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("FSHImporter Alias should resolve but log a warning when an alias contains unsupported characters")
      void resolveButLogWarningAliasContains() {
        // The only supported characters in Aliases are ASCII letters, numbers, _, -, .
        var input = leftAlign("\n"
                                   + "      Alias: B@dAlias = http://example.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = B@dAlias#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Alias.fsh")));
        assertEquals(1, results.size());
        AssignmentRule rule = (AssignmentRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        // The B@dAlias alias does still resolve because only a warning is logged
        assertToEqual(new FshCode("foo", "http://example.org").withFile("Alias.fsh").withLocation(new int[] {6, 10, 6, 21}), rule.value);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertTrue(matches(Pattern.compile("B@dAlias includes unsupported characters"), loggerSpy.getLastMessage("warn")));
      }

      @Test
      @DisplayName("FSHImporter Alias should resolve but log a warning when an alias contains unsupported characters and starts with $")
      void resolveButLogWarningAliasContains2() {
        // The only supported characters in Aliases are ASCII letters, numbers, _, -, .
        var input = leftAlign("\n"
                                   + "      Alias: $N*tAll*wed = http://example.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = $N*tAll*wed#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Alias.fsh")));
        assertEquals(1, results.size());
        AssignmentRule rule = (AssignmentRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        // The $N*tAll*wed alias does still resolve because only a warning is logged
        assertToEqual(new FshCode("foo", "http://example.org").withFile("Alias.fsh").withLocation(new int[] {6, 10, 6, 24}), rule.value);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertTrue(matches(Pattern.compile("\\$N\\*tAll\\*wed includes unsupported characters"), loggerSpy.getLastMessage("warn")));
      }

      @Test
      @DisplayName("FSHImporter Alias should resolve but log a warning when an alias contains ONLY a $")
      void resolveButLogWarningAliasContains3() {
        // Depending on how you read the FSH spec, this may technically be allowed FSH,
        // but SUSHI will warn about it.
        var input = leftAlign("\n"
                                   + "      Alias: $ = http://example.org\n"
                                   + "\n"
                                   + "      Profile: ObservationProfile\n"
                                   + "      Parent: Observation\n"
                                   + "      * code = $#foo\n"
                                   + "      ");
        var results = ImportText.importText(List.of(new RawFSH(input, "Alias.fsh")));
        assertEquals(1, results.size());
        AssignmentRule rule = (AssignmentRule) results.get(0).profiles.get("ObservationProfile").rules.get(0);
        // The $ alias does still resolve because only a warning is logged
        assertToEqual(new FshCode("foo", "http://example.org").withFile("Alias.fsh").withLocation(new int[] {6, 10, 6, 14}), rule.value);
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertTrue(matches(Pattern.compile("\\$ includes unsupported characters"), loggerSpy.getLastMessage("warn")));
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
