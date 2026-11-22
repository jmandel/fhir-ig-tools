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

// port of sushi/test/fshtypes/Logical.test.ts (SUSHI 3.21.1)
class LogicalTest {
  @Nested
  @DisplayName("Logical")
  class DLogical1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("Logical #constructor should set the properties correctly")
      void t3() throws Exception {
        var l = new Logical("MyLogical");
        assertEquals("MyLogical", l.name);
        assertEquals("MyLogical", l.getId());
        assertEquals("Base", l.parent);
        assertNull(l.title);
        assertNull(l.description);
        assertTrue(l.rules.isEmpty());
      }
      @Test
      @DisplayName("Logical #constructor should get the correct constructor name")
      void t4() throws Exception {
        var l = new Logical("MyLogical");
        assertEquals("Logical", l.constructorName());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh5 {
      @Test
      @DisplayName("Logical #toFSH should produce FSH for the simplest Logical model")
      void t6() throws Exception {
        var input = new Logical("MyModel");
        var expectedResult = String.join(EOL, "Logical: MyModel", "Parent: Base", "Id: MyModel");
        assertEquals(expectedResult, input.toFSH());
      }
      @Test
      @DisplayName("Logical #toFSH should produce FSH for a Logical model with additional metadata")
      void t7() throws Exception {
        var input = new Logical("MyModel");
        input.setId("my-model");
        input.title = "My Model";
        input.description = "A new model for logical modeling.";
        input.characteristics = List.of("can-be-target", "has-size", "has-length");
        var expectedResult = String.join(EOL, "Logical: MyModel", "Parent: Base", "Id: my-model", "Title: \"My Model\"", "Description: \"A new model for logical modeling.\"", "Characteristics: #can-be-target, #has-size, #has-length");
        assertEquals(expectedResult, input.toFSH());
      }
      @Test
      @DisplayName("Logical #toFSH should produce FSH for a Logical model with rules")
      void t8() throws Exception {
        var input = new Logical("MyModel");
        var addElement = new AddElementRule("feature");
        addElement.min = 0.0;
        addElement.max = "11";
        addElement.types.add(OnlyRuleType.reference("Procedure"));
        var obeysRule = new ObeysRule("feature");
        obeysRule.invariant = "mm-1";
        input.rules.addAll(List.of(addElement, obeysRule));
        var expectedResult = String.join(EOL, "Logical: MyModel", "Parent: Base", "Id: MyModel", "* feature 0..11 Reference(Procedure)", "* feature obeys mm-1");
        assertEquals(expectedResult, input.toFSH());
      }
    }
  }
}
