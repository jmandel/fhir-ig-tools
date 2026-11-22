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

// port of sushi/test/fshtypes/rules/AssignmentRule.test.ts (SUSHI 3.21.1)
class AssignmentRuleTest {
  @Nested
  @DisplayName("AssignmentRule")
  class DAssignmentrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("AssignmentRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var c = new AssignmentRule("component.code");
        assertEquals("component.code", c.path);
        assertNull(c.value);
        // TS: toBeUndefined() for both; Java models these optional flags as primitive false (every TS read of them
        // treats undefined as false)
        assertFalse(c.exactly);
        assertFalse(c.isInstance);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a value that matches exactly")
      void t5() throws Exception {
        var rule = new AssignmentRule("valueInteger");
        rule.value = 5.0;
        rule.exactly = true;
        assertEquals("* valueInteger = 5 (exactly)", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a boolean value")
      void t6() throws Exception {
        var rule = new AssignmentRule("valueBoolean");
        rule.value = true;
        assertEquals("* valueBoolean = true", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a number value")
      void t7() throws Exception {
        var rule = new AssignmentRule("valueDecimal");
        rule.value = 1.21;
        assertEquals("* valueDecimal = 1.21", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a string value")
      void t8() throws Exception {
        var rule = new AssignmentRule("note.text");
        rule.value = "This is the \"note text\".\nThis is the second line.";
        assertEquals("* note.text = \"This is the \\\"note text\\\".\\nThis is the second line.\"", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a FshCode value for a code element")
      void t9() throws Exception {
        var rule = new AssignmentRule("status");
        rule.value = new FshCode("final");
        assertEquals("* status = #final", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a FshCode value for a CodeableConcept element")
      void t10() throws Exception {
        var rule = new AssignmentRule("code");
        rule.value = new FshCode("573", "http://example.com/codes", "speed setting \\\"high\\\"");
        assertEquals("* code = http://example.com/codes#573 \"speed setting \\\\\\\"high\\\\\\\"\"", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a FshQuantity value")
      void t11() throws Exception {
        var rule = new AssignmentRule("valueQuantity");
        rule.value = new FshQuantity(1.21, new FshCode("GW", "http://unitsofmeasure.org"));
        assertEquals("* valueQuantity = 1.21 'GW'", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a FshRatio value")
      void t12() throws Exception {
        var rule = new AssignmentRule("valueRatio");
        rule.value = new FshRatio(new FshQuantity(5.0, new FshCode("cm", "http://unitsofmeasure.org")), new FshQuantity(1.0, new FshCode("s", "http://unitsofmeasure.org")));
        assertEquals("* valueRatio = 5 'cm' : 1 's'", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with a FshReference value")
      void t13() throws Exception {
        var rule = new AssignmentRule("subject");
        rule.value = new FshReference("http://example.com/PaulBunyan", "Paul Bunyan");
        assertEquals("* subject = Reference(http://example.com/PaulBunyan) \"Paul Bunyan\"", rule.toFSH());
      }
      @Test
      @DisplayName("AssignmentRule #toFSH should produce FSH for an AssignmentRule with an InstanceDefinition value")
      void t14() throws Exception {
        var rule = new AssignmentRule("note");
        var value = new org.hl7.fhir.sushi.fhirtypes.InstanceDefinition();
        value.setResourceType("Annotation");
        value.setId("Annotation0");
        rule.value = value;
        rule.isInstance = true;
        assertEquals("* note = Annotation0", rule.toFSH());
      }
    }
  }
}
