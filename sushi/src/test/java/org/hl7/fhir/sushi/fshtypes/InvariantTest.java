package org.hl7.fhir.sushi.fshtypes;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/Invariant.test.ts (SUSHI 3.21.1)
class InvariantTest {
  @Nested
  @DisplayName("Invariant")
  class DInvariant1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("Invariant #constructor should set initial properties correctly")
      void t3() throws Exception {
        var invariant = new Invariant("MyInvariant");
        assertEquals("MyInvariant", invariant.name);
        assertNull(invariant.description);
        assertNull(invariant.expression);
        assertNull(invariant.xpath);
        assertNull(invariant.severity);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("Invariant #toFSH should produce FSH for the simplest invariant")
      void t5() throws Exception {
        var input = new Invariant("inv-1");
        var expectedResult = "Invariant: inv-1";
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Invariant #toFSH should produce FSH for an invariant with additional metadata")
      void t6() throws Exception {
        var input = new Invariant("inv-2");
        input.description = "This is an important condition.";
        input.severity = new FshCode("error");
        input.expression = "requirement.exists()";
        input.xpath = "f:requirement";
        var expectedResult = String.join(EOL, "Invariant: inv-2", "Description: \"This is an important condition.\"", "* severity = #error", "* expression = \"requirement.exists()\"", "* xpath = \"f:requirement\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Invariant #toFSH should produce FSH for an invariant with metadata that contains characters that are escaped in FSH")
      void t7() throws Exception {
        var input = new Invariant("inv-3");
        input.description = "Please do this.\nPlease always do this with a \\ character.";
        input.severity = new FshCode("warning");
        input.expression = "requirement.contains(\"\\\")";
        input.xpath = "f:requirement";
        var expectedResult = String.join(EOL, "Invariant: inv-3", "Description: \"\"\"Please do this.\nPlease always do this with a \\ character.\"\"\"", "* severity = #warning", "* expression = \"requirement.contains(\\\"\\\\\\\")\"", "* xpath = \"f:requirement\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Invariant #toFSH should produce FSH for an invariant with a multiline expression")
      void t8() throws Exception {
        var input = new Invariant("inv-3a");
        input.description = "This invariant has a multiline expression.";
        input.severity = new FshCode("error");
        input.expression = "(cage.exists() and aquarium.exists()) or\n(habitat.exists() and enclosure.exists())";
        input.xpath = "f:requirement";
        var expectedResult = String.join(EOL, "Invariant: inv-3a", "Description: \"This invariant has a multiline expression.\"", "* severity = #error", "* expression = \"\"\"(cage.exists() and aquarium.exists()) or\n(habitat.exists() and enclosure.exists())\"\"\"", "* xpath = \"f:requirement\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Invariant #toFSH should produce FSH for an invariant with additional rules")
      void t9() throws Exception {
        var input = new Invariant("inv-4");
        input.description = "This is an important condition.";
        input.severity = new FshCode("error");
        input.expression = "requirement.exists()";
        input.xpath = "f:requirement";
        var requirements = new AssignmentRule("requirements");
        requirements.value = "This is necessary because it is important.";
        var extensionUrl = new AssignmentRule("human.extension[0].url");
        extensionUrl.value = "http://example.org/SomeExtension";
        var extensionValue = new AssignmentRule("human.extension[0].valueString");
        extensionValue.value = "ExtensionValue";
        input.rules.addAll(List.of(requirements, extensionUrl, extensionValue));
        var expectedResult = String.join(EOL, "Invariant: inv-4", "Description: \"This is an important condition.\"", "* severity = #error", "* expression = \"requirement.exists()\"", "* xpath = \"f:requirement\"", "* requirements = \"This is necessary because it is important.\"", "* human.extension[0].url = \"http://example.org/SomeExtension\"", "* human.extension[0].valueString = \"ExtensionValue\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
    }
  }
}
