package org.hl7.fhir.sushi.fshtypes;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/Resource.test.ts (SUSHI 3.21.1)
class ResourceTest {
  @Nested
  @DisplayName("Resource")
  class DResource1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("Resource #constructor should set the properties correctly")
      void t3() throws Exception {
        var r = new Resource("MyResource");
        assertEquals("MyResource", r.name);
        assertEquals("MyResource", r.getId());
        assertEquals("DomainResource", r.parent);
        assertNull(r.title);
        assertNull(r.description);
        assertTrue(r.rules.isEmpty());
      }
      @Test
      @DisplayName("Resource #constructor should get the correct constructor name")
      void t4() throws Exception {
        var r = new Resource("MyResource");
        assertEquals("Resource", r.constructorName());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh5 {
      @Test
      @DisplayName("Resource #toFSH should produce FSH for the simplest Resource")
      void t6() throws Exception {
        var input = new Resource("MyResource");
        var expectedResult = String.join(EOL, "Resource: MyResource", "Parent: DomainResource", "Id: MyResource");
        assertEquals(expectedResult, input.toFSH());
      }
      @Test
      @DisplayName("Resource #toFSH should produce FSH for a Resource with additional metadata")
      void t7() throws Exception {
        var input = new Resource("MyResource");
        input.setId("my-resource");
        input.title = "My Resource";
        input.description = "The new standard in resourcefulness.";
        var expectedResult = String.join(EOL, "Resource: MyResource", "Parent: DomainResource", "Id: my-resource", "Title: \"My Resource\"", "Description: \"The new standard in resourcefulness.\"");
        assertEquals(expectedResult, input.toFSH());
      }
      @Test
      @DisplayName("Resource #toFSH should produce FSH for a Resource with rules")
      void t8() throws Exception {
        var input = new Resource("Cookie");
        var addElement = new AddElementRule("ingredient");
        addElement.min = 1.0;
        addElement.max = "*";
        addElement.summary = true;
        addElement.types.add(new OnlyRuleType("CodeableConcept"));
        var bindingRule = new BindingRule("ingredient");
        bindingRule.valueSet = "BakingVS";
        bindingRule.strength = "preferred";
        input.rules.addAll(List.of(addElement, bindingRule));
        var expectedResult = String.join(EOL, "Resource: Cookie", "Parent: DomainResource", "Id: Cookie", "* ingredient 1..* SU CodeableConcept", "* ingredient from BakingVS (preferred)");
        assertEquals(expectedResult, input.toFSH());
      }
    }
  }
}
