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

// port of sushi/test/fshtypes/FshValueSet.test.ts (SUSHI 3.21.1)
class FshValueSetTest {
  @Nested
  @DisplayName("ValueSet")
  class DValueset1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("ValueSet #constructor should set the properties correctly")
      void t3() throws Exception {
        var vs = new FshValueSet("MyValueSet");
        assertEquals("MyValueSet", vs.name);
        assertEquals("MyValueSet", vs.getId());
        assertNull(vs.title);
        assertNull(vs.description);
        assertTrue(vs.rules.isEmpty());
      }
    }
    @Nested
    @DisplayName("#id")
    class DId4 {
      @Test
      @DisplayName("ValueSet #id should return an id set by a string caret rule")
      void t5() throws Exception {
        var p = new FshValueSet("MyValueSet");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        p.rules.add(idRule);
        assertEquals("different-id", p.getId());
      }
      @Test
      @DisplayName("ValueSet #id should not return an id set by a non-string caret rule")
      void t6() throws Exception {
        var p = new FshValueSet("MyValueSet");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = BigInteger.valueOf(23456L);
        idRule.rawValue = "23456";
        p.rules.add(idRule);
        assertEquals("MyValueSet", p.getId());
      }
      @Test
      @DisplayName("ValueSet #id should not return an id set by an instance caret rule")
      void t7() throws Exception {
        var p = new FshValueSet("MyValueSet");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        idRule.isInstance = true;
        p.rules.add(idRule);
        assertEquals("MyValueSet", p.getId());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh8 {
      @Test
      @DisplayName("ValueSet #toFSH should produce FSH for the simplest ValueSet")
      void t9() throws Exception {
        var vs = new FshValueSet("MyValueSet");
        var expectedResult = String.join(EOL, "ValueSet: MyValueSet", "Id: MyValueSet");
        assertEquals(expectedResult, vs.toFSH());
      }
      @Test
      @DisplayName("ValueSet #toFSH should produce FSH for a ValueSet with additional metadata")
      void t10() throws Exception {
        var vs = new FshValueSet("MyValueSet");
        vs.setId("my-value-set");
        vs.title = "My Value Set";
        vs.description = "This is my newest value set.";
        var expectedResult = String.join(EOL, "ValueSet: MyValueSet", "Id: my-value-set", "Title: \"My Value Set\"", "Description: \"This is my newest value set.\"");
        assertEquals(expectedResult, vs.toFSH());
      }
      @Test
      @DisplayName("ValueSet #toFSH should produce FSH for a ValueSet with rules")
      void t11() throws Exception {
        var vs = new FshValueSet("MyValueSet");
        var conceptRule = new ValueSetConceptComponentRule(true);
        conceptRule.from.system = "GoodCodes";
        conceptRule.concepts.add(new FshCode("goodthing"));
        var filterRule = new ValueSetFilterComponentRule(false);
        filterRule.from.valueSets = List.of("BadVS", "TerribleVS");
        vs.rules.addAll(List.of(conceptRule, filterRule));
        var expectedResult = String.join(EOL, "ValueSet: MyValueSet", "Id: MyValueSet", "* GoodCodes#goodthing", "* exclude codes from valueset BadVS and TerribleVS");
        assertEquals(expectedResult, vs.toFSH());
      }
    }
  }
}
