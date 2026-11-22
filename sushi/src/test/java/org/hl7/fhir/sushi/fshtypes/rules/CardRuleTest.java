package org.hl7.fhir.sushi.fshtypes.rules;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/rules/CardRule.test.ts (SUSHI 3.21.1)
class CardRuleTest {
  @Nested
  @DisplayName("CardRule")
  class DCardrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("CardRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var c = new CardRule("component.code");
        assertEquals("component.code", c.path);
        assertNull(c.min);
        assertNull(c.max);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("CardRule #toFSH should produce FSH for a CardRule with a min and a max")
      void t5() throws Exception {
        var rule = new CardRule("name");
        rule.min = 2.0;
        rule.max = "8";
        assertEquals("* name 2..8", rule.toFSH());
      }
      @Test
      @DisplayName("CardRule #toFSH should produce FSH for a CardRule with only a min")
      void t6() throws Exception {
        var rule = new CardRule("photo");
        rule.min = 3.0;
        assertEquals("* photo 3..", rule.toFSH());
      }
      @Test
      @DisplayName("CardRule #toFSH should produce FSH for a CardRule with only a max")
      void t7() throws Exception {
        var rule = new CardRule("contact");
        rule.min = Double.NaN;
        // the importer sets min to a result from parseInt, which may be NaN
        rule.max = "5";
        assertEquals("* contact ..5", rule.toFSH());
      }
    }
  }
}
