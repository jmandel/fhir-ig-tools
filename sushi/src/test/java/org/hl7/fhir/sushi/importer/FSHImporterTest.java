package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.testhelpers.Asserts.*;
import static org.hl7.fhir.sushi.testhelpers.ImportSingleText.importSingleText;
import static org.hl7.fhir.sushi.testhelpers.LeftAlign.leftAlign;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Profile;
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

    @Test @DisplayName("should default filename to blank string")
    void defaultFilename() {
      FSHDocument result = importSingleText("");
      assertEquals("", result.file);
    }

    @Test @DisplayName("should retain the passed in file name")
    void retainFileName() {
      FSHDocument result = importSingleText("", "FSHImporter.test.ts");
      assertEquals("FSHImporter.test.ts", result.file);
    }

    @Test @DisplayName("should allow a blank FSH document")
    void blankDocument() {
      FSHDocument result = importSingleText("");
      assertEquals(0, result.aliases.size());
      assertEquals(0, result.profiles.size());
      assertEquals(0, result.extensions.size());
    }

    @Test @DisplayName("should recover from extraneous input errors from antlr")
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

    @Test @DisplayName("should parse escaped double-quote and backslash characters in strings")
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

    @Test @DisplayName("should parse escaped unicode characters in strings and multiline strings")
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
  }
}
