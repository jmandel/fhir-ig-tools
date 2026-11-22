package org.hl7.fhir.sushi.fshtypes;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/Profile.test.ts (SUSHI 3.21.1)
class ProfileTest {
  @Nested
  @DisplayName("Profile")
  class DProfile1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("Profile #constructor should set the properties correctly")
      void t3() throws Exception {
        var p = new Profile("MyProfile");
        assertEquals("MyProfile", p.name);
        assertEquals("MyProfile", p.getId());
        assertNull(p.parent);
        assertTrue(p.rules.isEmpty());
      }
    }
    @Nested
    @DisplayName("#id")
    class DId4 {
      @Test
      @DisplayName("Profile #id should return an id set by a string caret rule")
      void t5() throws Exception {
        var p = new Profile("MyObservation");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        p.rules.add(idRule);
        assertEquals("different-id", p.getId());
      }
      @Test
      @DisplayName("Profile #id should not return an id set by a non-string caret rule")
      void t6() throws Exception {
        var p = new Profile("MyObservation");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = BigInteger.valueOf(23456L);
        idRule.rawValue = "23456";
        p.rules.add(idRule);
        assertEquals("MyObservation", p.getId());
      }
      @Test
      @DisplayName("Profile #id should not return an id set by an instance caret rule")
      void t7() throws Exception {
        var p = new Profile("MyObservation");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        idRule.isInstance = true;
        p.rules.add(idRule);
        assertEquals("MyObservation", p.getId());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh8 {
      @Test
      @DisplayName("Profile #toFSH should produce FSH for the simplest profile")
      void t9() throws Exception {
        var input = new Profile("SimpleProfile");
        var expectedResult = String.join(EOL, "Profile: SimpleProfile", "Id: SimpleProfile");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Profile #toFSH should produce FSH for a profile with additional metadata")
      void t10() throws Exception {
        var input = new Profile("MyObservation");
        input.parent = "Observation";
        input.setId("my-observation");
        input.title = "My Observation";
        input.description = "My profile on Observation.";
        var expectedResult = String.join(EOL, "Profile: MyObservation", "Parent: Observation", "Id: my-observation", "Title: \"My Observation\"", "Description: \"My profile on Observation.\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Profile #toFSH should produce FSH for a profile with metadata that contains characters that are escaped in FSH")
      void t11() throws Exception {
        var input = new Profile("NewlineProfile");
        input.setId("newline-profile");
        input.title = "This title\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?";
        input.description = "This description\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?";
        var expectedResult = String.join(EOL, "Profile: NewlineProfile", "Id: newline-profile", "Title: \"This title\\nhas a newline in it. Is that \\\\not allowed\\\\? Is that \\\"not okay\\\"?\"", "Description: \"\"\"This description\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?\"\"\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Profile #toFSH should produce FSH for a profile with rules")
      void t12() throws Exception {
        var input = new Profile("MyPatient");
        input.parent = "Patient";
        var cardRule = new CardRule("name");
        cardRule.min = 2.0;
        cardRule.max = "8";
        input.rules.add(cardRule);
        var flagRule = new FlagRule("active");
        flagRule.mustSupport = true;
        flagRule.summary = true;
        input.rules.add(flagRule);
        var bindingRule = new BindingRule("maritalStatus");
        bindingRule.valueSet = "http://example.org/ValueSet/MaritalStatus";
        bindingRule.strength = "required";
        input.rules.add(bindingRule);
        var obeysRule = new ObeysRule("contact");
        obeysRule.invariant = "myp-1";
        input.rules.add(obeysRule);
        var expectedResult = String.join(EOL, "Profile: MyPatient", "Parent: Patient", "Id: MyPatient", "* name 2..8", "* active MS SU", "* maritalStatus from http://example.org/ValueSet/MaritalStatus (required)", "* contact obeys myp-1");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
    }
  }
}
