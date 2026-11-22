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

// port of sushi/test/fshtypes/rules/ContainsRule.test.ts (SUSHI 3.21.1)
class ContainsRuleTest {
  @Nested
  @DisplayName("ContainsRule")
  class DContainsrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("ContainsRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var c = new ContainsRule("component.code");
        assertEquals("component.code", c.path);
        assertEquals(0.0, c.items.size());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("ContainsRule #toFSH should produce FSH for a ContainsRule with one item")
      void t5() throws Exception {
        // NOTE: All contains rules must have a card rule, so this case is purely for testing purposes.
        var rule = new ContainsRule("component");
        rule.items = List.of(new ContainsRuleItem("systolic"));
        assertEquals("* component contains systolic 0..", rule.toFSH());
      }
      @Test
      @DisplayName("ContainsRule #toFSH should produce FSH for a ContainsRule with one named item")
      void t6() throws Exception {
        // NOTE: All contains rules must have a card rule, so this case is purely for testing purposes.
        var rule = new ContainsRule("component");
        rule.items = List.of(new ContainsRuleItem("systolic", "SystolicBP"));
        assertEquals("* component contains SystolicBP named systolic 0..", rule.toFSH());
      }
      @Test
      @DisplayName("ContainsRule #toFSH should produce FSH for a ContainsRule with multiple items")
      void t7() throws Exception {
        // NOTE: All contains rules must have a card rule, so this case is purely for testing purposes.
        var rule = new ContainsRule("component");
        rule.items = List.of(new ContainsRuleItem("systolic", "SystolicBP"), new ContainsRuleItem("diastolic", "DiastolicBP"));
        var expectedFSH = "* component contains" + (EOL) + "    SystolicBP named systolic 0.. and" + (EOL) + "    DiastolicBP named diastolic 0..";
        assertEquals(expectedFSH, rule.toFSH());
      }
    }
  }
}
