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

// port of sushi/test/fshtypes/rules/ConceptRule.test.ts (SUSHI 3.21.1)
class ConceptRuleTest {
  @Nested
  @DisplayName("ConceptRule")
  class DConceptrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("ConceptRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var c = new ConceptRule("foo", "bar", "baz");
        assertEquals("", c.path);
        assertEquals("foo", c.code);
        assertEquals("bar", c.display);
        assertEquals("baz", c.definition);
        assertEquals(0, c.hierarchy.size());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with only code")
      void t5() throws Exception {
        var rule = new ConceptRule("foo");
        var expectedResult = "* #foo";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with code and display")
      void t6() throws Exception {
        var rule = new ConceptRule("foo");
        rule.display = "bar";
        var expectedResult = "* #foo \"bar\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with code, display, and definition")
      void t7() throws Exception {
        var rule = new ConceptRule("foo");
        rule.display = "bar";
        rule.definition = "baz";
        var expectedResult = "* #foo \"bar\" \"baz\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with code and definition")
      void t8() throws Exception {
        var rule = new ConceptRule("foo");
        rule.definition = "baz";
        var expectedResult = "* #foo \"\"\"baz\"\"\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with display and definition that contain characters that are escaped in FSH")
      void t9() throws Exception {
        var rule = new ConceptRule("foo");
        rule.display = "bar is \"important\" \\really\\";
        rule.definition = "baz is \"pretend\" \\but not really\\";
        var expectedResult = "* #foo \"bar is \\\"important\\\" \\\\really\\\\\" \"baz is \\\"pretend\\\" \\\\but not really\\\\\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with a code that has spaces")
      void t10() throws Exception {
        var rule = new ConceptRule("foo with a space");
        rule.display = "bar";
        rule.definition = "baz";
        var expectedResult = "* #\"foo with a space\" \"bar\" \"baz\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with a code that has tabs")
      void t11() throws Exception {
        var rule = new ConceptRule("foo\twith\ta\ttab");
        rule.display = "bar";
        rule.definition = "baz";
        var expectedResult = "* #\"foo\\twith\\ta\\ttab\" \"bar\" \"baz\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with a code that starts with quotes")
      void t12() throws Exception {
        var rule = new ConceptRule("\"quotefoo\"");
        rule.display = "bar";
        rule.definition = "baz";
        var expectedResult = "* #\"\\\"quotefoo\\\"\" \"bar\" \"baz\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with one code in its hierarchy")
      void t13() throws Exception {
        var rule = new ConceptRule("platinum");
        rule.display = "platinum";
        rule.definition = "element with atomic number 78";
        rule.hierarchy = List.of("metal");
        var expectedResult = "* #metal #platinum \"platinum\" \"element with atomic number 78\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("ConceptRule #toFSH should produce FSH for a ConceptRule with multiple codes in its hierarchy")
      void t14() throws Exception {
        var rule = new ConceptRule("platinum");
        rule.display = "platinum";
        rule.definition = "element with atomic number 78";
        rule.hierarchy = List.of("physical", "solid phase", "metal");
        var expectedResult = "* #physical #\"solid phase\" #metal #platinum \"platinum\" \"element with atomic number 78\"";
        var result = rule.toFSH();
        assertEquals(expectedResult, result);
      }
    }
  }
}
