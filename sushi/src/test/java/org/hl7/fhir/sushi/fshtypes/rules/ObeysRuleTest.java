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

// port of sushi/test/fshtypes/rules/ObeysRule.test.ts (SUSHI 3.21.1)
class ObeysRuleTest {
  @Nested
  @DisplayName("ObeysRule")
  class DObeysrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("ObeysRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var c = new ObeysRule("component.code");
        assertEquals("component.code", c.path);
        assertNull(c.invariant);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("ObeysRule #toFSH should produce FSH for an ObeysRule on a non-root element")
      void t5() throws Exception {
        var rule = new ObeysRule("component");
        rule.invariant = "FSH-1";
        assertEquals("* component obeys FSH-1", rule.toFSH());
      }
      @Test
      @DisplayName("ObeysRule #toFSH should produce FSH for an ObeysRule on the root element")
      void t6() throws Exception {
        var rule = new ObeysRule(".");
        rule.invariant = "FSH-5";
        assertEquals("* obeys FSH-5", rule.toFSH());
      }
    }
  }
}
