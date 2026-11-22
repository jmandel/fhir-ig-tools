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

// port of sushi/test/fshtypes/ParamRuleSet.test.ts (SUSHI 3.21.1)
class ParamRuleSetTest {
  @Nested
  @DisplayName("ParamRuleSet")
  class DParamruleset1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("ParamRuleSet #constructor should set initial properties correctly")
      void t3() throws Exception {
        var ruleSet = new ParamRuleSet("MyParamRuleSet");
        assertEquals("MyParamRuleSet", ruleSet.name);
        assertEquals(List.of(), ruleSet.parameters);
        assertEquals("", ruleSet.contents);
      }
    }
    @Nested
    @DisplayName("#getUnusedParameters")
    class DGetunusedparameters4 {
      @Test
      @DisplayName("ParamRuleSet #getUnusedParameters should return a list of all unused parameters")
      void t5() throws Exception {
        var ruleSet = new ParamRuleSet("MyParamRuleSet");
        ruleSet.parameters = List.of("first", "second", "third", "fourth");
        ruleSet.contents = String.join(EOL, "* code from {first}", "* category from {third}");
        var unusedParameters = ruleSet.getUnusedParameters();
        assertEquals(List.of("second", "fourth"), unusedParameters);
      }
      @Test
      @DisplayName("ParamRuleSet #getUnusedParameters should detect parameter uses that contain whitespace")
      void t6() throws Exception {
        var ruleSet = new ParamRuleSet("MyParamRuleSet");
        ruleSet.parameters = List.of("first", "second", "third", "fourth");
        ruleSet.contents = String.join(EOL, "* code from { first  }", "* category from {\tthird \t}");
        var unusedParameters = ruleSet.getUnusedParameters();
        assertEquals(List.of("second", "fourth"), unusedParameters);
      }
      @Test
      @DisplayName("ParamRuleSet #getUnusedParameters should return an empty list when all parameters are used")
      void t7() throws Exception {
        var ruleSet = new ParamRuleSet("MyParamRuleSet");
        ruleSet.parameters = List.of("first", "second", "third", "fourth");
        ruleSet.contents = String.join(EOL, "* code from {first}", "* category from {third}", "* note {second}..{fourth}");
        var unusedParameters = ruleSet.getUnusedParameters();
        assertEquals(List.of(), unusedParameters);
      }
    }
  }
}
