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

// port of sushi/test/fshtypes/Extension.test.ts (SUSHI 3.21.1)
class ExtensionTest {
  @Nested
  @DisplayName("Extension")
  class DExtension1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("Extension #constructor should set the properties correctly")
      void t3() throws Exception {
        var p = new Extension("MyExtension");
        assertEquals("MyExtension", p.name);
        assertEquals("MyExtension", p.getId());
        assertEquals("Extension", p.parent);
        assertTrue(p.rules.isEmpty());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("Extension #toFSH should produce FSH for the simplest extension")
      void t5() throws Exception {
        var input = new Extension("SimpleExtension");
        var expectedResult = String.join(EOL, "Extension: SimpleExtension", "Parent: Extension", "Id: SimpleExtension");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Extension #toFSH should produce FSH for an extension with additional metadata")
      void t6() throws Exception {
        var input = new Extension("MyExtension");
        input.parent = "Extension";
        input.setId("my-extension");
        input.title = "My Extension";
        input.description = "My extension is not very extensive.";
        input.contexts = List.of(new ExtensionContext("my.fhirpath(expression, \"context\")", true), new ExtensionContext("SomeOtherExtension", false));
        var expectedResult = String.join(EOL, "Extension: MyExtension", "Parent: Extension", "Id: my-extension", "Title: \"My Extension\"", "Description: \"My extension is not very extensive.\"", "Context: \"my.fhirpath(expression, \\\"context\\\")\", SomeOtherExtension");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Extension #toFSH should produce FSH for an extension with rules")
      void t7() throws Exception {
        var input = new Extension("MyExtension");
        var cardRule = new CardRule("extension");
        cardRule.min = 0.0;
        cardRule.max = "0";
        input.rules.add(cardRule);
        var flagRule = new FlagRule("value[x]");
        flagRule.mustSupport = true;
        flagRule.summary = true;
        input.rules.add(flagRule);
        var bindingRule = new BindingRule("value[x]");
        bindingRule.valueSet = "http://example.org/ValueSet/Foo";
        bindingRule.strength = "required";
        input.rules.add(bindingRule);
        var obeysRule = new ObeysRule(".");
        obeysRule.invariant = "myx-1";
        input.rules.add(obeysRule);
        var expectedResult = String.join(EOL, "Extension: MyExtension", "Parent: Extension", "Id: MyExtension", "* extension 0..0", "* value[x] MS SU", "* value[x] from http://example.org/ValueSet/Foo (required)", "* obeys myx-1");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
    }
  }
}
