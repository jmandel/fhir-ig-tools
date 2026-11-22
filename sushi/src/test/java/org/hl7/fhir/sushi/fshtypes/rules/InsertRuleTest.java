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

// port of sushi/test/fshtypes/rules/InsertRule.test.ts (SUSHI 3.21.1)
class InsertRuleTest {
  @Nested
  @DisplayName("InsertRule")
  class DInsertrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("InsertRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var i = new InsertRule("foo");
        assertNull(i.ruleSet);
        assertTrue(i.params.isEmpty());
        assertEquals("foo", i.path);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("InsertRule #toFSH should produce FSH for an InsertRule without parameters or a path")
      void t5() throws Exception {
        var rule = new InsertRule("");
        rule.ruleSet = "MyRuleSet";
        assertEquals("* insert MyRuleSet", rule.toFSH());
      }
      @Test
      @DisplayName("InsertRule #toFSH should produce FSH for an InsertRule with parameters and no path")
      void t6() throws Exception {
        var rule = new InsertRule("");
        rule.ruleSet = "MyRuleSet";
        rule.params = List.of("this\\that", "oh, no", "(big)", "more regular", " less regular ", "[[a]], ([[b]]), [[c]]");
        assertEquals("* insert MyRuleSet(this\\\\that, [[oh, no]], [[(big)]], more regular, [[ less regular ]], [[[[a]]\\, ([[b]]\\), [[c]]]])", rule.toFSH());
      }
      @Test
      @DisplayName("InsertRule #toFSH should produce FSH for an InsertRule with a path and no parameters")
      void t7() throws Exception {
        var rule = new InsertRule("name.family");
        rule.ruleSet = "MyRuleSet";
        assertEquals("* name.family insert MyRuleSet", rule.toFSH());
      }
      @Test
      @DisplayName("InsertRule #toFSH should produce FSH for an InsertRule with a code path and no parameters")
      void t8() throws Exception {
        var rule = new InsertRule("");
        rule.ruleSet = "MyRuleSet";
        rule.pathArray = List.of("pizza", "bagelpizza");
        assertEquals("* #pizza #bagelpizza insert MyRuleSet", rule.toFSH());
      }
      @Test
      @DisplayName("InsertRule #toFSH should produce FSH for an InsertRule with a path and parameters")
      void t9() throws Exception {
        var rule = new InsertRule("name.family");
        rule.ruleSet = "MyRuleSet";
        rule.params = List.of("this\\that", "oh, no", "(big)", "more regular", " less regular ");
        assertEquals("* name.family insert MyRuleSet(this\\\\that, [[oh, no]], [[(big)]], more regular, [[ less regular ]])", rule.toFSH());
      }
    }
  }
}
