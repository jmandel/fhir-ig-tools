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

// port of sushi/test/fshtypes/rules/OnlyRule.test.ts (SUSHI 3.21.1)
class OnlyRuleTest {
  @Nested
  @DisplayName("OnlyRule")
  class DOnlyrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("OnlyRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var t = new OnlyRule("component.code");
        assertEquals("component.code", t.path);
        assertTrue(t.types.isEmpty());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with one non-Reference type")
      void t5() throws Exception {
        var rule = new OnlyRule("value[x]");
        rule.types = List.of(new OnlyRuleType("Quantity"));
        assertEquals("* value[x] only Quantity", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with multiple non-Reference types")
      void t6() throws Exception {
        var rule = new OnlyRule("value[x]");
        rule.types = List.of(new OnlyRuleType("Quantity"), new OnlyRuleType("string"));
        assertEquals("* value[x] only Quantity or string", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with one Reference type")
      void t7() throws Exception {
        var rule = new OnlyRule("basedOn");
        rule.types = List.of(OnlyRuleType.reference("FooReferenceProfile"));
        assertEquals("* basedOn only Reference(FooReferenceProfile)", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with multiple Reference types")
      void t8() throws Exception {
        var rule = new OnlyRule("basedOn");
        rule.types = List.of(OnlyRuleType.reference("FooReferenceProfile"), OnlyRuleType.reference("BarReferenceProfile"));
        assertEquals("* basedOn only Reference(FooReferenceProfile or BarReferenceProfile)", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with one canonical type")
      void t9() throws Exception {
        var rule = new OnlyRule("library");
        rule.types = List.of(OnlyRuleType.canonical("FooCanonicalProfile"));
        assertEquals("* library only Canonical(FooCanonicalProfile)", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with multiple canonical types")
      void t10() throws Exception {
        var rule = new OnlyRule("library");
        rule.types = List.of(OnlyRuleType.canonical("FooCanonicalProfile"), OnlyRuleType.canonical("BarCanonicalProfile"));
        assertEquals("* library only Canonical(FooCanonicalProfile or BarCanonicalProfile)", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with one CodeableReference type")
      void t11() throws Exception {
        var rule = new OnlyRule("basedOn");
        rule.types = List.of(OnlyRuleType.codeableReference("FooCodeableReferenceProfile"));
        assertEquals("* basedOn only CodeableReference(FooCodeableReferenceProfile)", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with multiple CodeableReference types")
      void t12() throws Exception {
        var rule = new OnlyRule("basedOn");
        rule.types = List.of(OnlyRuleType.codeableReference("FooCodeableReferenceProfile"), OnlyRuleType.codeableReference("BarCodeableReferenceProfile"));
        assertEquals("* basedOn only CodeableReference(FooCodeableReferenceProfile or BarCodeableReferenceProfile)", rule.toFSH());
      }
      @Test
      @DisplayName("OnlyRule #toFSH should produce FSH for an OnlyRule with Reference and non-Reference types")
      void t13() throws Exception {
        var rule = new OnlyRule("value[x]");
        rule.types = List.of(OnlyRuleType.reference("FooReferenceProfile"), OnlyRuleType.reference("BarReferenceProfile"), OnlyRuleType.canonical("FooCanonicalProfile"), OnlyRuleType.canonical("BarCanonicalProfile"), OnlyRuleType.codeableReference("FooCodeableReferenceProfile"), OnlyRuleType.codeableReference("BarCodeableReferenceProfile"), new OnlyRuleType("Quantity"), new OnlyRuleType("string"));
        assertEquals("* value[x] only Quantity or string or Reference(FooReferenceProfile or BarReferenceProfile) or Canonical(FooCanonicalProfile or BarCanonicalProfile) or CodeableReference(FooCodeableReferenceProfile or BarCodeableReferenceProfile)", rule.toFSH());
      }
    }
  }
}
