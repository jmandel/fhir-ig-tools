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

// port of sushi/test/fshtypes/Mapping.test.ts (SUSHI 3.21.1)
class MappingTest {
  @Nested
  @DisplayName("Mapping")
  class DMapping1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("Mapping #constructor should set the properties correctly")
      void t3() throws Exception {
        var m = new Mapping("MyMapping");
        assertEquals("MyMapping", m.name);
        assertEquals("MyMapping", m.id);
        assertNull(m.source);
        assertNull(m.target);
        assertNull(m.description);
        assertNull(m.title);
        assertTrue(m.rules.isEmpty());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("Mapping #toFSH should produce FSH for the simplest Mapping")
      void t5() throws Exception {
        var input = new Mapping("SimpleMapping");
        var expectedResult = String.join(EOL, "Mapping: SimpleMapping", "Id: SimpleMapping");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Mapping #toFSH should produce FSH for a Mapping with additional metadata")
      void t6() throws Exception {
        var input = new Mapping("MetaMapping");
        input.id = "meta-mapping";
        input.title = "Meta Mapping";
        input.description = "This is a Mapping with some metadata";
        input.source = "ProfiledPatient";
        input.target = "http://example.org";
        var expectedResult = String.join(EOL, "Mapping: MetaMapping", "Id: meta-mapping", "Title: \"Meta Mapping\"", "Description: \"This is a Mapping with some metadata\"", "Source: ProfiledPatient", "Target: \"http://example.org\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Mapping #toFSH should produce FSH for a Mapping with metadata that contains characters that are escaped in FSH")
      void t7() throws Exception {
        var input = new Mapping("NewLineMapping");
        input.id = "new-line-mapping";
        input.target = "http://crazy\\url.com";
        input.title = "This title\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?";
        input.description = "This description\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?";
        var expectedResult = String.join(EOL, "Mapping: NewLineMapping", "Id: new-line-mapping", "Title: \"This title\\nhas a newline in it. Is that \\\\not allowed\\\\? Is that \\\"not okay\\\"?\"", "Description: \"\"\"This description\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?\"\"\"", "Target: \"http://crazy\\\\url.com\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("Mapping #toFSH should produce FSH for a Mapping with rules")
      void t8() throws Exception {
        var input = new Mapping("MyMapping");
        var mappingRule1 = new MappingRule("foo");
        mappingRule1.map = "bar.baz";
        input.rules.add(mappingRule1);
        var mappingRule2 = new MappingRule("oof");
        mappingRule2.map = "rab.zab";
        input.rules.add(mappingRule2);
        var expectedResult = String.join(EOL, "Mapping: MyMapping", "Id: MyMapping", "* foo -> \"bar.baz\"", "* oof -> \"rab.zab\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
    }
  }
}
