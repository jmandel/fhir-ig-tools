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
import org.hl7.fhir.sushi.testhelpers.LoggerSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/FSHImporter.test.ts (SUSHI 3.21.1)
class FSHImporterTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void reset() {
      loggerSpy.reset();
    }

    @Test @DisplayName("FSHImporter should default filename to blank string")
    void defaultFilename() {
      FSHDocument result = importSingleText("");
      assertEquals("", result.file);
    }

    @Test @DisplayName("FSHImporter should retain the passed in file name")
    void retainFileName() {
      FSHDocument result = importSingleText("", "FSHImporter.test.ts");
      assertEquals("FSHImporter.test.ts", result.file);
    }

    @Test @DisplayName("FSHImporter should allow a blank FSH document")
    void blankDocument() {
      FSHDocument result = importSingleText("");
      assertEquals(0, result.aliases.size());
      assertEquals(0, result.profiles.size());
      assertEquals(0, result.extensions.size());
    }

    @Test @DisplayName("FSHImporter should recover from extraneous input errors from antlr")
    void recoverExtraneous() {
      String input = leftAlign("""

          OOPS!

          Profile: Foo
          Parent: FooDad
          """);
      FSHDocument result = importSingleText(input, "Extra.fsh");
      Profile profile = result.profiles.get("Foo");
      assertEquals("Foo", profile.name);
      assertEquals("FooDad", profile.parent);
      assertTrue(Pattern.compile("File: Extra\\.fsh.*Line: 2\\D*", Pattern.DOTALL)
          .matcher(loggerSpy.getLastMessage("error")).find());
    }

    @Test @DisplayName("FSHImporter should parse escaped double-quote and backslash characters in strings")
    void escapedQuotes() {
      String input = leftAlign("""

          Profile: Escape
          Parent: Observation
          Title: "Can \\"you\\" escape \\\\ this \\\\ string?"
          * code = #"This \\\\ code \\"is quite\\" challenging \\\\to escape"
          """);
      FSHDocument result = importSingleText(input, "Escape.fsh");
      Profile profile = result.profiles.get("Escape");
      assertEquals("Can \"you\" escape \\ this \\ string?", profile.title);
      FshCode expectedCode = new FshCode("This \\ code \"is quite\" challenging \\to escape")
          .withLocation(new int[] {5, 10, 5, 61}).withFile("Escape.fsh");
      assertEquals(1, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "code", expectedCode);
    }

    @Test @DisplayName("FSHImporter should parse escaped hash and backslash characters in system identifiers in codings")
    void escapedHash() {
      String input = leftAlign("""

          Profile: HashBrowns
          Parent: Observation
          * code = https://breakfast.com/good\\\\food\\#potatoes#hash#browns
          * extraCode = https://lastly.com/backslash\\\\#last
          * bonusCode = \\\\\\\\#just_backslash "Just Backslash"
          """);
      FSHDocument result = importSingleText(input, "HashBrowns.fsh");
      Profile profile = result.profiles.get("HashBrowns");
      FshCode expectedCode = new FshCode("hash#browns", "https://breakfast.com/good\\food#potatoes")
          .withLocation(new int[] {4, 10, 4, 63}).withFile("HashBrowns.fsh");
      FshCode expectedExtraCode = new FshCode("last", "https://lastly.com/backslash\\")
          .withLocation(new int[] {5, 15, 5, 49}).withFile("HashBrowns.fsh");
      FshCode expectedBonusCode = new FshCode("just_backslash", "\\\\", "Just Backslash")
          .withLocation(new int[] {6, 15, 6, 50}).withFile("HashBrowns.fsh");
      assertEquals(3, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "code", expectedCode);
      assertAssignmentRule(profile.rules.get(1), "extraCode", expectedExtraCode);
      assertAssignmentRule(profile.rules.get(2), "bonusCode", expectedBonusCode);
    }

    @Test @DisplayName("FSHImporter should parse escaped unicode characters in strings and multiline strings")
    void escapedUnicode() {
      String input = leftAlign("""

          Profile: Escape
          Parent: Observation
          Title: "Just going to \\u270E in some unicode"
          * valueString = "This cool \\u2603 can read music in \\uD834\\uDD1E!"
          * category.text = \"""
            When there is a sign of \\u2744, often school is canceled.
            Kids can stay home and play with \\uD83C\\uDC31 or do anything!
          \"""
          """);
      FSHDocument result = importSingleText(input, "Escape.fsh");
      Profile profile = result.profiles.get("Escape");
      String expectedCategoryText = String.join("\n", "When there is a sign of ❄, often school is canceled.",
          "Kids can stay home and play with 🀱 or do anything!");
      assertEquals("Just going to ✎ in some unicode", profile.title);
      assertEquals(2, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "valueString", "This cool ☃ can read music in 𝄞!");
      assertAssignmentRule(profile.rules.get(1), "category.text", expectedCategoryText);
    }
    @Test @DisplayName("FSHImporter should parse unescaped unicode characters in strings and multiline strings")
    void unescapedUnicode() {
      String input = leftAlign("""

          Profile: Escape
          Parent: Observation
          Title: "Just going to ✎ in some unicode"
          * valueString = "This cool ☃ can read music in 𝄞!"
          * category.text = \"""
            When there is a sign of ❄, often school is canceled.
            Kids can stay home and play with 🀱 or do anything!
          \"""
          """);
      FSHDocument result = importSingleText(input, "Escape.fsh");
      Profile profile = result.profiles.get("Escape");
      String expectedCategoryText = String.join("\n", "When there is a sign of ❄, often school is canceled.",
          "Kids can stay home and play with 🀱 or do anything!");
      assertEquals("Just going to ✎ in some unicode", profile.title);
      assertEquals(2, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "valueString", "This cool ☃ can read music in 𝄞!");
      assertAssignmentRule(profile.rules.get(1), "category.text", expectedCategoryText);
    }

    @Test @DisplayName("FSHImporter should parse a code that has an unmatched quote (\") at the start of the code")
    void unmatchedQuote() {
      String input = "\n    Profile: MismatchedQuote\n    Parent: Observation\n"
          + "    * code = https://breakfast.com/goodfood#\"potatoes\n    ";
      FSHDocument result = importSingleText(input, "MismatchedQuote.fsh");
      Profile profile = result.profiles.get("MismatchedQuote");
      FshCode expectedCode = new FshCode("\"potatoes", "https://breakfast.com/goodfood")
          .withLocation(new int[] {4, 14, 4, 53}).withFile("MismatchedQuote.fsh");
      assertEquals(1, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "code", expectedCode);
    }

    @Test @DisplayName("FSHImporter should parse a rule that uses non-breaking spaces in a concept string")
    void nonBreakingConcept() {
      String input = leftAlign("\n    Profile: NonBreakingObservation\n    Parent: Observation\n"
          + "    * code = #\"These are non-breaking.\"\n    ");
      FSHDocument result = importSingleText(input, "NonBreaking.fsh");
      Profile profile = result.profiles.get("NonBreakingObservation");
      FshCode expectedCode = new FshCode("These are non-breaking.")
          .withLocation(new int[] {4, 10, 4, 35}).withFile("NonBreaking.fsh");
      assertEquals(1, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "code", expectedCode);
    }

    @Test @DisplayName("FSHImporter should log an error when a concept string starts with whitespace")
    void conceptLeadingWhitespace() {
      String input = leftAlign("\n    Profile: NonBreakingObservation\n    Parent: Observation\n"
          + "    * code = #\" Leading whitespace prohibited.\"\n    ");
      FSHDocument result = importSingleText(input, "NonBreaking.fsh");
      result.profiles.get("NonBreakingObservation");
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertTrue(dotall("extraneous input.*File: NonBreaking\\.fsh.*Line: 4\\D*", loggerSpy.getLastMessage("error")));
    }

    @Test @DisplayName("FSHImporter should parse a rule on an element named system")
    void elementNamedSystem() {
      String input = leftAlign("""

          Profile: MyOperation
          Parent: OperationDefinition
          * system = true
          """);
      FSHDocument result = importSingleText(input, "MyOperation.fsh");
      Profile profile = result.profiles.get("MyOperation");
      assertEquals(1, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "system", true);
    }

    @Test @DisplayName("FSHImporter should parse rules on a list of paths that includes system")
    void listOfPathsWithSystem() {
      String input = leftAlign("""

          Profile: TrialOperationDefinition
          Parent: OperationDefinition
          * system and type TU
          * instance and system and type SU
          * instance and system MS
          """);
      FSHDocument result = importSingleText(input, "TrialOperationDefinition.fsh");
      Profile profile = result.profiles.get("TrialOperationDefinition");
      assertEquals(7, profile.rules.size());
      assertFlagRule(profile.rules.get(0), "system", null, null, null, true, null, null);
      assertFlagRule(profile.rules.get(1), "type", null, null, null, true, null, null);
      assertFlagRule(profile.rules.get(2), "instance", null, true, null, null, null, null);
      assertFlagRule(profile.rules.get(3), "system", null, true, null, null, null, null);
      assertFlagRule(profile.rules.get(4), "type", null, true, null, null, null, null);
      assertFlagRule(profile.rules.get(5), "instance", true, null, null, null, null, null);
      assertFlagRule(profile.rules.get(6), "system", true, null, null, null, null, null);
    }

    @Test @DisplayName("FSHImporter should parse a rule on an element named exclude")
    void elementNamedExclude() {
      String input = leftAlign("""

          Profile: MyGroup
          Parent: Group
          * characteristic
            * exclude MS
          """);
      FSHDocument result = importSingleText(input, "MyGroup.fsh");
      Profile profile = result.profiles.get("MyGroup");
      assertEquals(1, profile.rules.size());
      assertFlagRule(profile.rules.get(0), "characteristic.exclude", true, null, null, null, null, null);
    }

    @Test @DisplayName("FSHImporter should parse entity names that look like dates or times")
    void dateLikeNames() {
      String input = leftAlign("""


          Alias: 1950-04-01 = http://aprilfools.com

          Profile: 2000-01
          Parent: Observation
          * obeys 2000-10-31T00:01:02.000+00:00

          Profile: 2001
          Parent: 2000-01

          Extension: 2000-10
          * value[x] from 2000-10-31

          ValueSet: 2000-10-31
          * 2000-10-31T00#2001-01
          * 1950-04-01#2001-01-01

          CodeSystem: 2000-10-31T00
          * #2001-01 "Jan 2001" "January 2001"

          Logical: 2000-10-31T00:01
          * 1999-12-31 0..1 dateTime "Party" "Party like it's 1999"

          Resource: 2000-10-31T00:01:02
          Parent: DomainResource
          * contained only 2000

          Instance: 2000-10-31T00:01:02.000
          InstanceOf: Observation
          * insert 12:30
          * code = #123

          Instance: 1800-02-28
          InstanceOf: 2000-10-31T00:01
          * 1999-12-31 = 2000-01-01

          Invariant: 2000-10-31T00:01:02.000+00:00
          Description: "It shall not defy the laws of physics nor the laws of men"
          Severity: #error

          Mapping: 12
          Source: 2000-10-31T00:01
          Target: "http://unknown.org/mystery"
          * 1999-12-31 -> "Patient"

          RuleSet: 12:30
          * status = #final
          """);
      FSHDocument doc = importSingleText(input);
      assertEquals(1, doc.aliases.size());
      assertEquals("http://aprilfools.com", doc.aliases.get("1950-04-01"));
      assertEquals(2, doc.profiles.size());
      Profile p1 = doc.profiles.get("2000-01");
      assertEquals("2000-01", p1.name);
      assertEquals("2000-10-31T00:01:02.000+00:00", ((ObeysRule) p1.rules.get(0)).invariant);
      Profile p2 = doc.profiles.get("2001");
      assertEquals("2001", p2.name);
      assertEquals("2000-01", p2.parent);
      assertEquals(1, doc.extensions.size());
      Extension e1 = doc.extensions.get("2000-10");
      assertEquals("2000-10", e1.name);
      assertEquals("2000-10-31", ((BindingRule) e1.rules.get(0)).valueSet);
      assertEquals(1, doc.valueSets.size());
      FshValueSet vs1 = doc.valueSets.get("2000-10-31");
      assertEquals("2000-10-31", vs1.name);
      assertEquals("2000-10-31T00", ((ValueSetConceptComponentRule) vs1.rules.get(0)).concepts.get(0).system);
      assertEquals("2001-01", ((ValueSetConceptComponentRule) vs1.rules.get(0)).concepts.get(0).code);
      assertEquals("http://aprilfools.com", ((ValueSetConceptComponentRule) vs1.rules.get(1)).concepts.get(0).system);
      assertEquals("2001-01-01", ((ValueSetConceptComponentRule) vs1.rules.get(1)).concepts.get(0).code);
      assertEquals(1, doc.codeSystems.size());
      FshCodeSystem cs1 = doc.codeSystems.get("2000-10-31T00");
      assertEquals("2000-10-31T00", cs1.name);
      assertEquals("2001-01", ((ConceptRule) cs1.rules.get(0)).code);
      assertEquals(1, doc.logicals.size());
      Logical l1 = doc.logicals.get("2000-10-31T00:01");
      assertEquals("2000-10-31T00:01", l1.name);
      assertEquals("1999-12-31", ((AddElementRule) l1.rules.get(0)).path);
      assertEquals(1, doc.resources.size());
      Resource r1 = doc.resources.get("2000-10-31T00:01:02");
      assertEquals("2000-10-31T00:01:02", r1.name);
      assertEquals("2000", ((OnlyRule) r1.rules.get(0)).types.get(0).type);
      assertEquals(2, doc.instances.size());
      Instance i1 = doc.instances.get("2000-10-31T00:01:02.000");
      assertEquals("2000-10-31T00:01:02.000", i1.name);
      assertEquals("12:30", ((InsertRule) i1.rules.get(0)).ruleSet);
      Instance i2 = doc.instances.get("1800-02-28");
      assertEquals("1800-02-28", i2.name);
      assertEquals("2000-10-31T00:01", i2.instanceOf);
      assertEquals("1999-12-31", ((AssignmentRule) i2.rules.get(0)).path);
      assertEquals(1, doc.invariants.size());
      Invariant inv1 = doc.invariants.get("2000-10-31T00:01:02.000+00:00");
      assertEquals("2000-10-31T00:01:02.000+00:00", inv1.name);
      assertEquals(1, doc.mappings.size());
      Mapping m1 = doc.mappings.get("12");
      assertEquals("12", m1.name);
      assertEquals("2000-10-31T00:01", m1.source);
      assertEquals("1999-12-31", ((MappingRule) m1.rules.get(0)).path);
      assertEquals(1, doc.ruleSets.size());
      RuleSet rs1 = doc.ruleSets.get("12:30");
      assertEquals("12:30", rs1.name);
      assertTrue(loggerSpy.getAllLogs("error").isEmpty());
      assertTrue(loggerSpy.getAllLogs("warn").isEmpty());
    }

    @Test @DisplayName("FSHImporter should allow two FSH documents")
    void twoDocuments() {
      List<FSHDocument> results = ImportText.importText(List.of(new RawFSH(""), new RawFSH("")));
      assertEquals(2, results.size());
      for (FSHDocument r : results) {
        assertEquals(0, r.aliases.size());
        assertEquals(0, r.profiles.size());
        assertEquals(0, r.extensions.size());
      }
    }

    @Test @DisplayName("FSHImporter should allow two FSH documents even when first is invalid")
    void twoDocumentsFirstInvalid() {
      List<FSHDocument> results = ImportText.importText(
          List.of(new RawFSH("invalid FSH string", "Invalid.fsh"), new RawFSH("", "Blank.fsh")));
      assertEquals(2, results.size());
      for (FSHDocument r : results) {
        assertEquals(0, r.aliases.size());
        assertEquals(0, r.profiles.size());
        assertEquals(0, r.extensions.size());
      }
    }

    @Test @DisplayName("FSHImporter should allow a FSH document with a line comment at EOF without newline")
    void lineCommentAtEof() {
      String input = leftAlign("\n    Profile: ObservationProfile\n    Parent: Observation\n    //Comment");
      FSHDocument result = importSingleText(input);
      assertEquals(1, result.profiles.size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test @DisplayName("FSHImporter should allow a FSH document with a block comment at EOF without newline")
    void blockCommentAtEof() {
      String input = leftAlign("\n    Profile: ObservationProfile\n    Parent: Observation\n    /*\n    Comment\n"
          + "    Comment\n    */");
      FSHDocument result = importSingleText(input);
      assertEquals(1, result.profiles.size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test @DisplayName("FSHImporter should allow a FSH document with a single-line block comment")
    void singleLineBlockComment() {
      String input = leftAlign("""

          Profile: ObservationProfile
          Parent: Observation
          /* This comment is just one line */
          Title: "Single line comment test"
          """);
      FSHDocument result = importSingleText(input);
      assertEquals(1, result.profiles.size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test @DisplayName("FSHImporter should allow a FSH document with a block comment containing no whitespace")
    void blockCommentNoWhitespace() {
      String input = leftAlign("""

          Profile: ObservationProfile
          Parent: Observation
          /******comment*****/
          Title: "Single line comment test"
          """);
      FSHDocument result = importSingleText(input);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(1, result.profiles.size());
    }

    @Test @DisplayName("FSHImporter should adjust indentation of multi-line strings that include blank lines")
    void multilineBlankLines() {
      String input = leftAlign("\n    Profile: ObservationProfile\n    Parent: Observation\n    Description: \"\"\"\n"
          + "    SUSHI stands for:\n      SUSHI\n      Unshortens\n      Short\n      Hand\n      Inputs\n\n"
          + "    Recursive acronyms are very popular these days.\n    \"\"\"");
      String expectedDescription = String.join("\n", "SUSHI stands for:", "  SUSHI", "  Unshortens", "  Short",
          "  Hand", "  Inputs", "", "Recursive acronyms are very popular these days.");
      FSHDocument result = importSingleText(input);
      assertEquals(expectedDescription, result.profiles.get("ObservationProfile").description);
    }

    @Test @DisplayName("FSHImporter should not change indentation of multi-line strings when there is at least one unindented non-blank line")
    void multilineUnindented() {
      String input = "\n    Profile: ObservationProfile\n    Parent: Observation\n"
          + "    Description: \"\"\"A long statement follows this.\n\nLong statement:\n\n"
          + " This statement is much longer than the previous statement.\"\"\"";
      String expectedDescription = String.join("\n", "A long statement follows this.", "", "Long statement:", "",
          " This statement is much longer than the previous statement.");
      FSHDocument result = importSingleText(input);
      assertEquals(expectedDescription, result.profiles.get("ObservationProfile").description);
    }

    @Test @DisplayName("FSHImporter should truncate whitespace lines in multi-line strings")
    void multilineTruncateWhitespace() {
      String input = leftAlign("\n    Profile: StaircaseObservation\n    Parent: Observation\n"
          + "    Description: \"\"\"\n    This\n      Description\n\n        Looks\n          Like\n\n"
          + "            A\n              Staircase\n                \"\"\"");
      String expectedDescription = String.join("\n", "This", "  Description", "", "    Looks", "      Like", "",
          "        A", "          Staircase");
      FSHDocument result = importSingleText(input);
      assertEquals(expectedDescription, result.profiles.get("StaircaseObservation").description);
    }

    @Test @DisplayName("FSHImporter should parse multi-line strings with CRLF line breaks")
    void multilineCrlf() {
      String input = String.join("\r\n", "Profile: ObservationProfile", "Parent: Observation", "Description: \"\"\"",
          "Line endings", "are always", "troublesome.", "\"\"\"");
      String expectedDescription = String.join("\n", "Line endings", "are always", "troublesome.");
      FSHDocument result = importSingleText(input);
      assertEquals(expectedDescription, result.profiles.get("ObservationProfile").description);
    }

    @Test @DisplayName("FSHImporter should parse multi-line strings that only occupy one line")
    void multilineOneLine() {
      String input = leftAlign("""

          Profile: ObservationProfile
          Parent: Observation
          Description: \"""Descriptions come in only one size.\"""
          """);
      FSHDocument result = importSingleText(input);
      assertEquals("Descriptions come in only one size.", result.profiles.get("ObservationProfile").description);
    }

    @Test @DisplayName("FSHImporter should parse the multiline string ending in a quote from issue #1569")
    void multilineEndingQuote() {
      String input = String.join("\n", "Instance: TestPatient", "InstanceOf: Patient",
          "* name.text = \"\"\"Porous Robert \"SpongeBob\"\"\"\"");
      FSHDocument result = importSingleText(input);
      Instance instance = result.instances.get("TestPatient");
      assertEquals(1, instance.rules.size());
      assertAssignmentRule(instance.rules.get(0), "name.text", "Porous Robert \"SpongeBob\"");
      assertTrue(loggerSpy.getAllMessages("error").isEmpty());
    }

    private void endingQuotes(String expected) {
      String input = String.join("\n", "Profile: EndingQuotesProfile", "Parent: Observation",
          "Description: \"\"\"" + expected + "\"\"\"");
      FSHDocument result = importSingleText(input);
      assertEquals(expected, result.profiles.get("EndingQuotesProfile").description);
      assertTrue(loggerSpy.getAllMessages("error").isEmpty());
    }

    @Test @DisplayName("FSHImporter should parse multiline string content ending in zero quote characters")
    void endingZeroQuotes() {
      endingQuotes("no trailing quote");
    }

    @Test @DisplayName("FSHImporter should parse multiline string content ending in one quote characters")
    void endingOneQuote() {
      endingQuotes("one trailing quote\"");
    }

    @Test @DisplayName("FSHImporter should parse multiline string content ending in two quote characters")
    void endingTwoQuotes() {
      endingQuotes("two trailing quotes\"\"");
    }

    @Test @DisplayName("FSHImporter should preserve ordinary quotes and non-delimiter quote runs inside multiline strings")
    void internalQuotes() {
      String expected = "ordinary \"quotes\" and a double run \"\" stay content";
      String input = String.join("\n", "Profile: InternalQuotesProfile", "Parent: Observation",
          "Description: \"\"\"" + expected + "\"\"\"");
      FSHDocument result = importSingleText(input);
      assertEquals(expected, result.profiles.get("InternalQuotesProfile").description);
      assertTrue(loggerSpy.getAllMessages("error").isEmpty());
    }

    @Test @DisplayName("FSHImporter should keep adjacent multiline strings and neighboring tokens separate")
    void adjacentMultiline() {
      String input = String.join("\n", "Instance: NeighboringStrings", "InstanceOf: Patient",
          "* name[0].text = \"\"\"first value\"\"\"", "* name[1].text = \"\"\"second value\"\"\"", "* active = true");
      FSHDocument result = importSingleText(input);
      Instance instance = result.instances.get("NeighboringStrings");
      assertEquals(3, instance.rules.size());
      assertAssignmentRule(instance.rules.get(0), "name[0].text", "first value");
      assertAssignmentRule(instance.rules.get(1), "name[1].text", "second value");
      assertAssignmentRule(instance.rules.get(2), "active", true);
      assertTrue(loggerSpy.getAllMessages("error").isEmpty());
    }

    @Test @DisplayName("FSHImporter should parse an empty multiline string")
    void emptyMultiline() {
      String input = String.join("\n", "Profile: EmptyMultilineProfile", "Parent: Observation",
          "Description: \"\"\"\"\"\"");
      FSHDocument result = importSingleText(input);
      assertEquals("", result.profiles.get("EmptyMultilineProfile").description);
      assertTrue(loggerSpy.getAllMessages("error").isEmpty());
    }

    @Test @DisplayName("FSHImporter should reject an unterminated multiline string")
    void unterminatedMultiline() {
      String input = String.join("\n", "Profile: UnterminatedMultilineProfile", "Parent: Observation",
          "Description: \"\"\"unterminated");
      FSHDocument result = importSingleText(input, "Unterminated.fsh");
      assertEquals("", result.profiles.get("UnterminatedMultilineProfile").description);
      assertFalse(loggerSpy.getAllMessages("error").isEmpty());
      assertTrue(dotall("File: Unterminated\\.fsh.*Line: 3\\D*", loggerSpy.getLastMessage("error")));
    }

    @Test @DisplayName("FSHImporter should parse non-breaking space characters as whitespace")
    void nonBreakingSpaces() {
      String input = leftAlign("\n    Profile: NonBreakingObservation\n    Parent: Observation \n    ");
      FSHDocument result = importSingleText(input);
      assertEquals("Observation", result.profiles.get("NonBreakingObservation").parent);
    }

    @Test @DisplayName("FSHImporter should properly parse a string with newline, return, and tab characters")
    void stringEscapes() {
      String input = leftAlign("""

          Profile: ObservationProfile
          Parent: Observation
          Description: "Here is a \\n new line with some \\t tabbed information. \\r The end."
          """);
      FSHDocument result = importSingleText(input);
      assertEquals(1, result.profiles.size());
      assertEquals("Here is a \n new line with some \t tabbed information. \r The end.",
          result.profiles.get("ObservationProfile").description);
    }

    @Test @DisplayName("FSHImporter should properly parse a string with an escaped newline")
    void escapedNewline() {
      String input = leftAlign("""

          Profile: ObservationProfile
          Parent: Observation
          Description: "Here is an escaped \\\\n newline character."
          """);
      FSHDocument result = importSingleText(input);
      assertEquals(1, result.profiles.size());
      assertEquals("Here is an escaped \\n newline character.", result.profiles.get("ObservationProfile").description);
    }

    @Test @DisplayName("FSHImporter should properly parse a multiline string with newline, return, and tab characters")
    void multilineEscapes() {
      String input = leftAlign("""

          Profile: ObservationProfile
          Parent: Observation
          Description:
            \"""
            This is a multi-string description
            with a couple of special characters.

            This special paragraph has info on a \\n new line. And it has some \\t tabbed information. \\r The end.
            \"""
          """);
      FSHDocument result = importSingleText(input);
      assertEquals(1, result.profiles.size());
      String expected = String.join("\n", "This is a multi-string description", "with a couple of special characters.",
          "", "This special paragraph has info on a \n new line. And it has some \t tabbed information. \r The end.");
      assertEquals(expected, result.profiles.get("ObservationProfile").description);
    }

    @Test @DisplayName("FSHImporter should parse numbers using exponential notation")
    void exponentialNumbers() {
      String input = leftAlign("""

          Instance: MyObservation
          InstanceOf: Observation
          * component[0].valueQuantity = 2.3E11 'kg'
          * component[1].valueQuantity = 6.453E+25 's'
          * component[2].valueInteger = 4.50e3
          * component[3].valueSampledData.period = 1.5e-3
          * component[3].valueSampledData.dimensions = 0.88e+6
          * component[3].valueSampledData.origin = 155e-8 'm'
          * component[4].valueInteger = 300.0e-1
          * extension[0].valueDecimal = 48000e-5
          """);
      FSHDocument result = importSingleText(input, "Exponential.fsh");
      assertEquals(1, result.instances.size());
      Instance instance = result.instances.get("MyObservation");
      assertAssignmentRule(instance.rules.get(0), "component[0].valueQuantity",
          new FshQuantity(2.3e11, new FshCode("kg", "http://unitsofmeasure.org").withFile("Exponential.fsh")
              .withLocation(new int[] {4, 39, 4, 42})).withFile("Exponential.fsh").withLocation(new int[] {4, 32, 4, 42}));
      assertAssignmentRule(instance.rules.get(1), "component[1].valueQuantity",
          new FshQuantity(6.453e25, new FshCode("s", "http://unitsofmeasure.org").withFile("Exponential.fsh")
              .withLocation(new int[] {5, 42, 5, 44})).withFile("Exponential.fsh").withLocation(new int[] {5, 32, 5, 44}));
      assertAssignmentRule(instance.rules.get(2), "component[2].valueInteger", BigInteger.valueOf(4500));
      assertAssignmentRule(instance.rules.get(3), "component[3].valueSampledData.period", 0.0015);
      assertAssignmentRule(instance.rules.get(4), "component[3].valueSampledData.dimensions",
          BigInteger.valueOf(880000));
      assertAssignmentRule(instance.rules.get(5), "component[3].valueSampledData.origin",
          new FshQuantity(155e-8, new FshCode("m", "http://unitsofmeasure.org").withFile("Exponential.fsh")
              .withLocation(new int[] {9, 49, 9, 51})).withFile("Exponential.fsh").withLocation(new int[] {9, 42, 9, 51}));
      assertAssignmentRule(instance.rules.get(6), "component[4].valueInteger", BigInteger.valueOf(30));
      assertAssignmentRule(instance.rules.get(7), "extension[0].valueDecimal", 0.48);
    }

    @Test @DisplayName("FSHImporter should log info messages during import")
    void infoMessages() {
      String input = leftAlign("""

          Profile: OneProfile
          Parent: Observation

          Extension: OneExtension

          ValueSet: OneValueSet

          CodeSystem: OneCodeSystem

          Logical: OneLogicalModel
          Parent: Base

          Resource: OneResource
          Parent: DomainResource

          Instance: OneInstance
          InstanceOf: Observation
          * status = #final
          * code = #123
          """);
      importSingleText(input);
      List<LoggerSpy.Log> allLogs = loggerSpy.getAllLogs();
      assertEquals(2, allLogs.size());
      assertTrue(allLogs.get(0).level().contains("info"));
      assertTrue(allLogs.get(0).message().contains("Preprocessed"));
      assertTrue(allLogs.get(1).level().contains("info"));
      assertTrue(allLogs.get(1).message().contains("Imported 6 definitions and 1 instance"));
    }

    @Test @DisplayName("FSHImporter should avoid crashing and log error messages because of mismatched input")
    void mismatchedInput() {
      String input = leftAlign("""

          Profile: "BadProfile"

          Profile: BetterProfile
          Id: "BadId"
          Description: BadDescription
          """);
      FSHDocument result = importSingleText(input, "Mismatch.fsh");
      List<String> messages = loggerSpy.getAllMessages("error");
      assertEquals(3, messages.size());
      assertTrue(dotall("BadProfile.*SEQUENCE.*File: Mismatch\\.fsh.*Line: 2\\D*", messages.get(0)));
      assertTrue(dotall("BadId.*SEQUENCE.*File: Mismatch\\.fsh.*Line: 5\\D*", messages.get(1)));
      assertTrue(dotall("BadDescription.*\\{STRING, MULTILINE_STRING\\}.*File: Mismatch\\.fsh.*Line: 6\\D*",
          messages.get(2)));
      assertNotNull(result);
    }

    @Test @DisplayName("FSHImporter should log no error when two entities of different types have the same name")
    void sameNameDifferentTypes() {
      String input = "\n      CodeSystem: Foo\n      Title: \"This\"\n\n      ValueSet: Foo\n      Title: \"That\"\n    ";
      FSHDocument result = importSingleText(input, "RepeatedName.fsh");
      assertEquals(1, result.codeSystems.size());
      assertEquals(1, result.valueSets.size());
      assertEquals("This", result.codeSystems.get("Foo").title);
      assertEquals("That", result.valueSets.get("Foo").title);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }
  }

  static boolean dotall(String re, String s) {
    return s != null && Pattern.compile(re, Pattern.DOTALL).matcher(s).find();
  }
}
