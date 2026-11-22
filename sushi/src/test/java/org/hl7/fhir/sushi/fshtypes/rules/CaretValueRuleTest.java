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

// port of sushi/test/fshtypes/rules/CaretValueRule.test.ts (SUSHI 3.21.1)
class CaretValueRuleTest {
  @Nested
  @DisplayName("CaretValueRule")
  class DCaretvaluerule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("CaretValueRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var c = new CaretValueRule("component.code");
        assertEquals("component.code", c.path);
        assertNull(c.caretPath);
        assertNull(c.value);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a string")
      void t5() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "short";
        rule.value = "Important \"value\" summary";
        assertEquals("* ^short = \"Important \\\"value\\\" summary\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning an instance")
      void t6() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "contained[0]";
        rule.isInstance = true;
        rule.value = "Foo";
        assertEquals("* ^contained[0] = Foo", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule with a path")
      void t7() throws Exception {
        var rule = new CaretValueRule("value[x]");
        rule.caretPath = "definition";
        rule.value = "This is the value";
        assertEquals("* value[x] ^definition = \"This is the value\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule at root path")
      void t8() throws Exception {
        var rule = new CaretValueRule(".");
        rule.caretPath = "short";
        rule.value = "Another important summary.";
        assertEquals("* . ^short = \"Another important summary.\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a boolean")
      void t9() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "abstract";
        rule.value = false;
        assertEquals("* ^abstract = false", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a number")
      void t10() throws Exception {
        var rule = new CaretValueRule("component");
        rule.caretPath = "min";
        rule.value = 1.0;
        assertEquals("* component ^min = 1", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshCanonical")
      void t11() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "url";
        rule.value = new FshCanonical("Observation");
        assertEquals("* ^url = Canonical(Observation)", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshCanonical with a version")
      void t12() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "url";
        var canonical = new FshCanonical("Example");
        canonical.version = "1.2.3";
        rule.value = canonical;
        assertEquals("* ^url = Canonical(Example|1.2.3)", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshCode with just a code")
      void t13() throws Exception {
        var rule = new CaretValueRule("component");
        rule.caretPath = "slicing.rules";
        rule.value = new FshCode("open");
        assertEquals("* component ^slicing.rules = #open", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshCode with a code and system")
      void t14() throws Exception {
        var rule = new CaretValueRule("component");
        rule.caretPath = "slicing.rules";
        rule.value = new FshCode("open", "http://foo.com");
        assertEquals("* component ^slicing.rules = http://foo.com#open", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshCode with a code, system, and display")
      void t15() throws Exception {
        var rule = new CaretValueRule("component");
        rule.caretPath = "slicing.rules";
        rule.value = new FshCode("open", "http://foo.com", "Display Text");
        assertEquals("* component ^slicing.rules = http://foo.com#open \"Display Text\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshQuantity")
      void t16() throws Exception {
        var rule = new CaretValueRule("value[x]");
        rule.caretPath = "maxValueQuantity";
        rule.value = new FshQuantity(15.0);
        assertEquals("* value[x] ^maxValueQuantity = 15", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshQuantity with a unit")
      void t17() throws Exception {
        var rule = new CaretValueRule("value[x]");
        rule.caretPath = "maxValueQuantity";
        var mm = new FshCode("mm", "http://unitsofmeasure.org");
        rule.value = new FshQuantity(15.0, mm);
        assertEquals("* value[x] ^maxValueQuantity = 15 'mm'", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshQuantity with a non-UCUM system")
      void t18() throws Exception {
        var rule = new CaretValueRule("value[x]");
        rule.caretPath = "maxValueQuantity";
        var mm = new FshCode("mm", "http://otherunits.org", "millimeters");
        rule.value = new FshQuantity(15.0, mm);
        assertEquals("* value[x] ^maxValueQuantity = 15 http://otherunits.org#mm \"millimeters\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshRatio")
      void t19() throws Exception {
        var rule = new CaretValueRule("value[x]");
        rule.caretPath = "patternRatio";
        var numerator = new FshQuantity(130.0);
        var denominator = new FshQuantity(1.0);
        rule.value = new FshRatio(numerator, denominator);
        assertEquals("* value[x] ^patternRatio = 130 : 1", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshRatio with units")
      void t20() throws Exception {
        var rule = new CaretValueRule("value[x]");
        rule.caretPath = "patternRatio";
        var mg = new FshCode("mg", "http://unitsofmeasure.org");
        var numerator = new FshQuantity(130.0, mg);
        var dL = new FshCode("dL", "http://unitsofmeasure.org");
        var denominator = new FshQuantity(1.0, dL);
        rule.value = new FshRatio(numerator, denominator);
        assertEquals("* value[x] ^patternRatio = 130 'mg' : 1 'dL'", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshReference")
      void t21() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "url";
        rule.value = new FshReference("Example");
        assertEquals("* ^url = Reference(Example)", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning a FshReference with display")
      void t22() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "url";
        rule.value = new FshReference("Example", "My Example");
        assertEquals("* ^url = Reference(Example) \"My Example\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule assigning an InstanceDefinition")
      void t23() throws Exception {
        var rule = new CaretValueRule("");
        rule.caretPath = "contact";
        var instance = new org.hl7.fhir.sushi.fhirtypes.InstanceDefinition();
        instance._instanceMeta.name = "MyContact";
        rule.value = instance;
        assertEquals("* ^contact = MyContact", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule with isCodeCaretRule assigning a string")
      void t24() throws Exception {
        var rule = new CaretValueRule("");
        rule.pathArray = List.of("#bear");
        rule.isCodeCaretRule = true;
        rule.caretPath = "display";
        rule.value = "Bear is \"here\"";
        assertEquals("* #bear ^display = \"Bear is \\\"here\\\"\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule with isCodeCaretRule where the path array includes systems")
      void t25() throws Exception {
        var rule = new CaretValueRule("");
        rule.pathArray = List.of("zoo#bear");
        rule.isCodeCaretRule = true;
        rule.caretPath = "display";
        rule.value = "Bear is \"here\"";
        assertEquals("* zoo#bear ^display = \"Bear is \\\"here\\\"\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a nested CaretValueRule with isCodeCaretRule assigning a FshCanonical")
      void t26() throws Exception {
        var rule = new CaretValueRule("");
        rule.pathArray = List.of("#owl", "#barnowl");
        rule.isCodeCaretRule = true;
        rule.caretPath = "extension.url";
        rule.value = new FshCanonical("OwlExtension");
        assertEquals("* #owl #barnowl ^extension.url = Canonical(OwlExtension)", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for CaretValueRule with isCodeCaretRule and no path")
      void t27() throws Exception {
        var rule = new CaretValueRule("");
        rule.isCodeCaretRule = true;
        rule.caretPath = "extension.url";
        rule.value = new FshCanonical("OwlExtension");
        assertEquals("* ^extension.url = Canonical(OwlExtension)", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRule with isCodeCaretRule where the code contains a space")
      void t28() throws Exception {
        var rule = new CaretValueRule("");
        rule.pathArray = List.of("#duck", "#big duck");
        rule.isCodeCaretRule = true;
        rule.caretPath = "display";
        rule.value = "is it a big duck or a goose?";
        assertEquals("* #duck #\"big duck\" ^display = \"is it a big duck or a goose?\"", rule.toFSH());
      }
      @Test
      @DisplayName("CaretValueRule #toFSH should produce FSH for a CaretValueRUle with isCodeCaretRule where the code starts with a double quote")
      void t29() throws Exception {
        var rule = new CaretValueRule("");
        rule.pathArray = List.of("zoo#\"bear\"");
        rule.isCodeCaretRule = true;
        rule.caretPath = "display";
        rule.value = "Bear is \"here\"";
        assertEquals("* zoo#\"\\\"bear\\\"\" ^display = \"Bear is \\\"here\\\"\"", rule.toFSH());
      }
    }
  }
}
