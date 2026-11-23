package org.hl7.fhir.sushi.fshtypes;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/FshCode.test.ts (SUSHI 3.21.1)
class FshCodeTest {
  @Nested
  @DisplayName("FshCode")
  class DFshcode1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("FshCode #constructor should set the properties correctly")
      void t3() throws Exception {
        var p = new FshCode("my-code", "http://important.com", "My Important Code");
        assertEquals("my-code", p.code);
        assertEquals("http://important.com", p.system);
        assertEquals("My Important Code", p.display);
      }
    }
    @Nested
    @DisplayName("#toString")
    class DTostring4 {
      @Test
      @DisplayName("FshCode #toString should return a string for a basic code")
      void t5() throws Exception {
        var code = new FshCode("my-code");
        var result = code.toString();
        assertEquals("#my-code", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code with system")
      void t6() throws Exception {
        var code = new FshCode("my-code", "http://foo.com");
        var result = code.toString();
        assertEquals("http://foo.com#my-code", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code with display")
      void t7() throws Exception {
        var code = new FshCode("my-code", null, "Display");
        var result = code.toString();
        assertEquals("#my-code \"Display\"", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code with system and display")
      void t8() throws Exception {
        var code = new FshCode("my-code", "http://foo.com", "Display");
        var result = code.toString();
        assertEquals("http://foo.com#my-code \"Display\"", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code with spaces")
      void t9() throws Exception {
        var code = new FshCode("my spacey code");
        var result = code.toString();
        assertEquals("#\"my spacey code\"", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code with tabs")
      void t10() throws Exception {
        var code = new FshCode("my\ttabby\tcode");
        var result = code.toString();
        assertEquals("#\"my\\ttabby\\tcode\"", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code that starts with a double quote")
      void t11() throws Exception {
        var code = new FshCode("\"my-code");
        var result = code.toString();
        assertEquals("#\"\\\"my-code\"", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code where the code has non-whitespace characters that must be escaped")
      void t12() throws Exception {
        var code = new FshCode("strange\\ \"code\"");
        var result = code.toString();
        assertEquals("#\"strange\\\\ \\\"code\\\"\"", result);
      }
      @Test
      @DisplayName("FshCode #toString should return a string for a code with a display where the display has non-whitespace characters that must be escaped")
      void t13() throws Exception {
        var code = new FshCode("strange-display", null, "very \"strange\"\\display");
        var result = code.toString();
        assertEquals("#strange-display \"very \\\"strange\\\"\\\\display\"", result);
      }
    }
  }
}
