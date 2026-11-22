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

// port of sushi/test/fshtypes/FshQuantity.test.ts (SUSHI 3.21.1)
class FshQuantityTest {
  @Nested
  @DisplayName("FshQuantity")
  class DFshquantity1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("FshQuantity #constructor should set the properties correctly")
      void t3() throws Exception {
        var quantity = new FshQuantity(100.0, new FshCode("mm", "http://unitsofmeasure.org", "millimeters"));
        assertEquals(100.0, quantity.value);
        assertEquals("mm", quantity.unit.code);
        assertEquals("http://unitsofmeasure.org", quantity.unit.system);
        assertEquals("millimeters", quantity.unit.display);
      }
    }
    @Nested
    @DisplayName("#toString")
    class DTostring4 {
      @Test
      @DisplayName("FshQuantity #toString should return string for quantity without unit")
      void t5() throws Exception {
        var quantity = new FshQuantity(100.0);
        var result = quantity.toString();
        assertEquals("100", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for basic unit code")
      void t6() throws Exception {
        var code = new FshCode("mm");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 #mm", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for basic unit code without value")
      void t7() throws Exception {
        var code = new FshCode("mm");
        var quantity = new FshQuantity(null, code);
        var result = quantity.toString();
        assertEquals("#mm", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with UCUM system")
      void t8() throws Exception {
        var code = new FshCode("mm", "http://unitsofmeasure.org");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 'mm'", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with UCUM system without value")
      void t9() throws Exception {
        var code = new FshCode("mm", "http://unitsofmeasure.org");
        var quantity = new FshQuantity(null, code);
        var result = quantity.toString();
        assertEquals("'mm'", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with non-UCUM system")
      void t10() throws Exception {
        var code = new FshCode("bar", "http://foo.com");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 http://foo.com#bar", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with non-UCUM system without value")
      void t11() throws Exception {
        var code = new FshCode("bar", "http://foo.com");
        var quantity = new FshQuantity(null, code);
        var result = quantity.toString();
        assertEquals("http://foo.com#bar", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with display")
      void t12() throws Exception {
        var code = new FshCode("mm", null, "Display");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 #mm \"Display\"", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with display without value")
      void t13() throws Exception {
        var code = new FshCode("mm", null, "Display");
        var quantity = new FshQuantity(null, code);
        var result = quantity.toString();
        assertEquals("#mm \"Display\"", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with UCUM system and display")
      void t14() throws Exception {
        var code = new FshCode("mm", "http://unitsofmeasure.org", "Display");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 'mm' \"Display\"", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with UCUM system and display without value")
      void t15() throws Exception {
        var code = new FshCode("mm", "http://unitsofmeasure.org", "Display");
        var quantity = new FshQuantity(null, code);
        var result = quantity.toString();
        assertEquals("'mm' \"Display\"", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with non-UCUM system and display")
      void t16() throws Exception {
        var code = new FshCode("bar", "http://foo.com", "Display");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 http://foo.com#bar \"Display\"", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with non-UCUM system and display without value")
      void t17() throws Exception {
        var code = new FshCode("bar", "http://foo.com", "Display");
        var quantity = new FshQuantity(null, code);
        var result = quantity.toString();
        assertEquals("http://foo.com#bar \"Display\"", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return string for unit code with code with spaces")
      void t18() throws Exception {
        var code = new FshCode("milli meters");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 #\"milli meters\"", result);
      }
      @Test
      @DisplayName("FshQuantity #toString should return a string for a unit code with UCUM system and a display containing characters that must be escaped")
      void t19() throws Exception {
        var code = new FshCode("mm", "http://unitsofmeasure.org", "strange\" display\\");
        var quantity = new FshQuantity(100.0, code);
        var result = quantity.toString();
        assertEquals("100 'mm' \"strange\\\" display\\\\\"", result);
      }
    }
  }
}
