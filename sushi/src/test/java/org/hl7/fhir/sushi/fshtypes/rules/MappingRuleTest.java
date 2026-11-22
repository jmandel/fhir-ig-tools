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

// port of sushi/test/fshtypes/rules/MappingRule.test.ts (SUSHI 3.21.1)
class MappingRuleTest {
  @Nested
  @DisplayName("MappingRule")
  class DMappingrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("MappingRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var m = new MappingRule("identifier");
        assertEquals("identifier", m.path);
        assertNull(m.map);
        assertNull(m.language);
        assertNull(m.comment);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a basic MappingRule")
      void t5() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.otherIdentifier";
        assertEquals("* identifier -> \"Patient.otherIdentifier\"", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with no path")
      void t6() throws Exception {
        var rule = new MappingRule("");
        rule.map = "Patient";
        assertEquals("* -> \"Patient\"", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with a comment")
      void t7() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.otherIdentifier";
        rule.comment = "This is a comment";
        assertEquals("* identifier -> \"Patient.otherIdentifier\" \"This is a comment\"", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with a language")
      void t8() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.otherIdentifier";
        rule.language = new FshCode("lang");
        assertEquals("* identifier -> \"Patient.otherIdentifier\" #lang", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with a comment and target")
      void t9() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.otherIdentifier";
        rule.comment = "This is a comment";
        rule.language = new FshCode("lang");
        assertEquals("* identifier -> \"Patient.otherIdentifier\" \"This is a comment\" #lang", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with a map that contains characters that are escaped in FSH")
      void t10() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.\\somethingFu\nnky";
        assertEquals("* identifier -> \"Patient.\\\\somethingFu\\nnky\"", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with a comment that contains characters that are escaped in FSH")
      void t11() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.otherIdentifier";
        rule.comment = "This has a \n newline, which is pretty \\wild.";
        assertEquals("* identifier -> \"Patient.otherIdentifier\" \"\"\"This has a \n newline, which is pretty \\wild.\"\"\"", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with a multiline comment")
      void t12() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.otherIdentifier";
        rule.comment = "This is a multiline comment\nthat spans multiple lines";
        assertEquals("* identifier -> \"Patient.otherIdentifier\" \"\"\"This is a multiline comment\nthat spans multiple lines\"\"\"", rule.toFSH());
      }
      @Test
      @DisplayName("MappingRule #toFSH should produce FSH for a MappingRule with a multiline comment and language")
      void t13() throws Exception {
        var rule = new MappingRule("identifier");
        rule.map = "Patient.otherIdentifier";
        rule.comment = "This is a multiline comment\nwith a language code";
        rule.language = new FshCode("lang");
        assertEquals("* identifier -> \"Patient.otherIdentifier\" \"\"\"This is a multiline comment\nwith a language code\"\"\" #lang", rule.toFSH());
      }
    }
  }
}
