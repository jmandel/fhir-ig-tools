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

// port of sushi/test/fshtypes/Instance.test.ts (SUSHI 3.21.1)
class InstanceTest {
  @Nested
  @DisplayName("Instance")
  class DInstance1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("Instance #constructor should set the properties correctly")
      void t3() throws Exception {
        var p = new Instance("MyInstance");
        assertEquals("MyInstance", p.name);
        assertEquals("MyInstance", p.getId());
        assertNull(p.instanceOf);
        assertNull(p.title);
        assertTrue(p.rules.isEmpty());
      }
    }
    @Nested
    @DisplayName("#id")
    class DId4 {
      @Test
      @DisplayName("Instance #id should return an id set by a string assignment rule")
      void t5() throws Exception {
        var p = new Instance("MyInstance");
        var idRule = new AssignmentRule("id");
        idRule.value = "different-id";
        p.rules.add(idRule);
        assertEquals("different-id", p.getId());
      }
      @Test
      @DisplayName("Instance #id should not return an id set by a non-string assignment rule")
      void t6() throws Exception {
        var p = new Instance("MyInstance");
        var idRule = new AssignmentRule("id");
        idRule.value = BigInteger.valueOf(12345L);
        idRule.rawValue = "12345";
        p.rules.add(idRule);
        // the id is still the default
        assertEquals("MyInstance", p.getId());
      }
      @Test
      @DisplayName("Instance #id should not return an id set by an instance assignment rule")
      void t7() throws Exception {
        var p = new Instance("MyInstance");
        var idRule = new AssignmentRule("id");
        idRule.value = "different-id";
        idRule.isInstance = true;
        p.rules.add(idRule);
        // the id is still the default
        assertEquals("MyInstance", p.getId());
      }
      @Test
      @DisplayName("Instance #id should return the last id set by assignment rules as the rules change")
      void t8() throws Exception {
        var p = new Instance("MyInstance");
        assertEquals("MyInstance", p.getId());
        var idRule = new AssignmentRule("id");
        idRule.value = "first-id";
        p.rules.add(idRule);
        assertEquals("first-id", p.getId());
        // a rule pushed after the first read must be found
        var secondIdRule = new AssignmentRule("id");
        secondIdRule.value = "second-id";
        p.rules.add(secondIdRule);
        assertEquals("second-id", p.getId());
        // a rule that replaces the last rule must be found
        var thirdIdRule = new AssignmentRule("id");
        thirdIdRule.value = "third-id";
        p.rules.remove(p.rules.size() - 1);
        p.rules.add(thirdIdRule);
        assertEquals("third-id", p.getId());
        // a change to the rule's value must be reflected
        thirdIdRule.value = "changed-id";
        assertEquals("changed-id", p.getId());
        // removing the rules must be reflected
        p.rules.remove(p.rules.size() - 1);
        p.rules.remove(p.rules.size() - 1);
        assertEquals("MyInstance", p.getId());
        // replacing the rules array (as applyInsertRules does) must be reflected
        p.rules = List.of(secondIdRule);
        assertEquals("second-id", p.getId());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh9 {
      @Test
      @DisplayName("Instance #toFSH should produce FSH for the simplest Instance")
      void t10() throws Exception {
        var i = new Instance("MyInstance");
        i.instanceOf = "Patient";
        var expectedResult = String.join(EOL, "Instance: MyInstance", "InstanceOf: Patient", "Usage: #example");
        assertEquals(expectedResult, i.toFSH());
      }
      @Test
      @DisplayName("Instance #toFSH should produce FSH for an instance with additional metadata")
      void t11() throws Exception {
        var i = new Instance("MyInstance");
        i.instanceOf = "Patient";
        i.title = "My Patient Title";
        i.description = "My Patient Description";
        var expectedResult = String.join(EOL, "Instance: MyInstance", "InstanceOf: Patient", "Title: \"My Patient Title\"", "Description: \"My Patient Description\"", "Usage: #example");
        assertEquals(expectedResult, i.toFSH());
      }
      @Test
      @DisplayName("Instance #toFSH should produce FSH for an instance with assignment rules")
      void t12() throws Exception {
        var exInstance = new Instance("MyInstance");
        exInstance.instanceOf = "Observation";
        var statusRule = new AssignmentRule("status");
        var statusCode = new FshCode("final");
        statusRule.value = statusCode;
        exInstance.rules.add(statusRule);
        var dateRule = new AssignmentRule("effectiveDateTime");
        dateRule.value = "2019-04-01";
        exInstance.rules.add(dateRule);
        var expectedResult = String.join(EOL, "Instance: MyInstance", "InstanceOf: Observation", "Usage: #example", "* status = #final", "* effectiveDateTime = \"2019-04-01\"");
        assertEquals(expectedResult, exInstance.toFSH());
      }
    }
  }
}
