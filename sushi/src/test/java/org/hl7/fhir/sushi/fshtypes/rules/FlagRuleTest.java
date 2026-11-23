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

// port of sushi/test/fshtypes/rules/FlagRule.test.ts (SUSHI 3.21.1)
class FlagRuleTest {
  @Nested
  @DisplayName("FlagRule")
  class DFlagrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("FlagRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var f = new FlagRule("component.code");
        assertEquals("component.code", f.path);
        assertNull(f.mustSupport);
        assertNull(f.summary);
        assertNull(f.modifier);
        assertNull(f.trialUse);
        assertNull(f.normative);
        assertNull(f.draft);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule with mustSupport")
      void t5() throws Exception {
        var rule = new FlagRule("name");
        rule.mustSupport = true;
        assertEquals("* name MS", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule with isModifier")
      void t6() throws Exception {
        var rule = new FlagRule("name");
        rule.modifier = true;
        assertEquals("* name ?!", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule with isSummary")
      void t7() throws Exception {
        var rule = new FlagRule("name");
        rule.summary = true;
        assertEquals("* name SU", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule with trialUse")
      void t8() throws Exception {
        var rule = new FlagRule("name");
        rule.trialUse = true;
        assertEquals("* name TU", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule with normative")
      void t9() throws Exception {
        var rule = new FlagRule("name");
        rule.normative = true;
        assertEquals("* name N", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule with draft")
      void t10() throws Exception {
        var rule = new FlagRule("name");
        rule.draft = true;
        assertEquals("* name D", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule with multiple valid flags")
      void t11() throws Exception {
        var rule = new FlagRule("name");
        rule.mustSupport = true;
        rule.summary = true;
        rule.trialUse = true;
        assertEquals("* name MS SU TU", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule and apply draft status over trialUse and normative")
      void t12() throws Exception {
        var rule = new FlagRule("name");
        rule.draft = true;
        rule.trialUse = true;
        rule.normative = true;
        assertEquals("* name D", rule.toFSH());
      }
      @Test
      @DisplayName("FlagRule #toFSH should produce FSH for a FlagRule and apply trialUse status over normative")
      void t13() throws Exception {
        var rule = new FlagRule("name");
        rule.trialUse = true;
        rule.normative = true;
        assertEquals("* name TU", rule.toFSH());
      }
    }
  }
}
