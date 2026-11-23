package org.hl7.fhir.sushi.fshtypes.rules;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/rules/BindingRule.test.ts (SUSHI 3.21.1)
class BindingRuleTest {
  @Nested
  @DisplayName("BindingRule")
  class DBindingrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("BindingRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var c = new BindingRule("component.code");
        assertEquals("component.code", c.path);
        assertNull(c.valueSet);
        assertNull(c.strength);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("BindingRule #toFSH should produce FSH for a BindingRule")
      void t5() throws Exception {
        var rule = new BindingRule("valueCodeableConcept");
        rule.valueSet = "http://example.org/ValueSet/Foo";
        rule.strength = "required";
        var expectedResult = "* valueCodeableConcept from http://example.org/ValueSet/Foo (required)";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
    }
  }
}
